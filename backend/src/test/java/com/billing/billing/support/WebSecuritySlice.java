package com.billing.billing.support;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import com.billing.billing.config.CorsConfig;
import com.billing.billing.security.JwtService;
import com.billing.billing.security.SecurityConfig;

// @WebMvcTest alone stubs out security, which would hide exactly what these controllers rely on
// (the JWT filter populating the principal, and @PreAuthorize role checks). This pulls the real
// SecurityConfig and its JwtService into the slice so requests are authenticated by real tokens.
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Import({SecurityConfig.class, CorsConfig.class, JwtService.class})
@ImportAutoConfiguration({SecurityAutoConfiguration.class, ServletWebSecurityAutoConfiguration.class,
        SecurityFilterAutoConfiguration.class})
@TestPropertySource(properties = {
        "app.jwt.secret=" + ApiTokens.SECRET,
        "app.jwt.expiration-ms=3600000"
})
public @interface WebSecuritySlice {
}
