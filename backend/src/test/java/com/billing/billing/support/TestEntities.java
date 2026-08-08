package com.billing.billing.support;

import java.lang.reflect.Field;
import java.math.BigDecimal;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import com.billing.billing.model.Invoice;
import com.billing.billing.model.InvoiceItem;
import com.billing.billing.model.PaymentMethod;
import com.billing.billing.model.Product;
import com.billing.billing.model.Role;
import com.billing.billing.model.Store;
import com.billing.billing.model.User;
import com.billing.billing.security.AuthenticatedUser;

import java.util.List;

// Entities deliberately expose no id setter (ids come from the DB) and no store setter, so unit
// tests that need an id-bearing, already-persisted-looking entity have to inject it reflectively.
public final class TestEntities {

    private TestEntities() {}

    public static Store store(long id, String name) {
        return withId(new Store(name), id);
    }

    public static Product product(long id, String sku, String price, String gstRate, int stockQuantity, Store store) {
        Product product = new Product("Product " + sku, sku, "desc", new BigDecimal(price),
                new BigDecimal(gstRate), "1234", stockQuantity, store);
        return withId(product, id);
    }

    public static User user(long id, String email, String passwordHash, Role role, Store store) {
        return withId(new User(email, passwordHash, role, store), id);
    }

    public static Invoice invoice(long id, Store store, String subtotal, String tax, PaymentMethod paymentMethod) {
        BigDecimal subtotalAmount = new BigDecimal(subtotal);
        BigDecimal taxAmount = new BigDecimal(tax);
        Invoice invoice = new Invoice("Customer", "9999999999", subtotalAmount, taxAmount,
                subtotalAmount.add(taxAmount), paymentMethod, store);
        return withId(invoice, id);
    }

    public static InvoiceItem invoiceItem(long id, Product product, int quantity, String unitPrice, String gstRate) {
        BigDecimal price = new BigDecimal(unitPrice);
        BigDecimal rate = new BigDecimal(gstRate);
        BigDecimal lineSubtotal = price.multiply(BigDecimal.valueOf(quantity));
        BigDecimal lineTax = lineSubtotal.multiply(rate).divide(BigDecimal.valueOf(100));
        InvoiceItem item = new InvoiceItem(product, product.getName(), product.getSku(), product.getHsnCode(),
                price, rate, quantity, lineSubtotal, lineTax, lineSubtotal.add(lineTax));
        return withId(item, id);
    }

    public static void authenticate(long userId, String email, Role role, long storeId) {
        AuthenticatedUser principal = new AuthenticatedUser(userId, email, role, storeId);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + role.name()))));
    }

    public static <T> T withId(T entity, long id) {
        try {
            Field field = entity.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
            return entity;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Could not set id on " + entity.getClass(), e);
        }
    }
}
