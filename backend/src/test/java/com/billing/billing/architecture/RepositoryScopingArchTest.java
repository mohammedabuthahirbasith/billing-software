package com.billing.billing.architecture;

import static com.tngtech.archunit.core.domain.JavaAccess.Predicates.target;
import static com.tngtech.archunit.core.domain.properties.HasName.Predicates.name;
import static com.tngtech.archunit.core.domain.properties.HasOwner.Predicates.With.owner;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import org.springframework.data.repository.CrudRepository;

import com.billing.billing.repository.StoreRepository;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

// Guards a residual gap the multi-tenancy retrofit couldn't close by construction: existsBySku/
// findBySku etc. were deleted outright so a missed call site fails to compile, but findAll()/
// findById() are inherited from JpaRepository and can't be deleted the same way. This test is the
// backstop — every current call site was already fixed manually, but nothing else stops a future
// line of code from calling one of these directly and reintroducing an unscoped, cross-tenant query.
@AnalyzeClasses(packages = "com.billing.billing", importOptions = ImportOption.DoNotIncludeTests.class)
public class RepositoryScopingArchTest {

    // StoreRepository is deliberately excluded: Store IS the tenant boundary, not tenant-owned data.
    // Every call site looks a store up by the current user's own storeId from their JWT, never by
    // an arbitrary/request-supplied id, so there's no cross-tenant lookup for this rule to catch.
    private static final DescribedPredicate<JavaClass> A_SPRING_DATA_REPOSITORY =
            DescribedPredicate.describe("a Spring Data repository (excluding StoreRepository)",
                    javaClass -> javaClass.isAssignableTo(CrudRepository.class)
                            && !javaClass.isAssignableTo(StoreRepository.class));

    @ArchTest
    static final ArchRule services_must_not_call_unscoped_find_methods =
            noClasses().that().resideInAPackage("com.billing.billing.service..")
                    .should().callMethodWhere(
                            target(owner(A_SPRING_DATA_REPOSITORY))
                                    .and(target(name("findById").or(name("findAll")))))
                    .because("findAll()/findById() are inherited and unscoped by store — every "
                            + "lookup must go through a Store-scoped repository method instead, or "
                            + "one store's data can leak to another");
}