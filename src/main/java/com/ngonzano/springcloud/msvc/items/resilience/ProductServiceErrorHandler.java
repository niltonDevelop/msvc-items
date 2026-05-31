package com.ngonzano.springcloud.msvc.items.resilience;

import java.util.concurrent.TimeoutException;

import org.springframework.web.reactive.function.client.WebClientResponseException;

import com.ngonzano.springcloud.msvc.items.exception.ProductTimeoutException;
import com.ngonzano.springcloud.msvc.items.exception.ProductUnavailableException;
import com.ngonzano.springcloud.msvc.items.exception.ProductUpstreamException;

import feign.FeignException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;

public final class ProductServiceErrorHandler {

    private ProductServiceErrorHandler() {
    }

    public static <T> T rethrow(Throwable throwable) {
        throw toException(throwable);
    }

    public static RuntimeException toException(Throwable throwable) {
        if (findCause(throwable, TimeoutException.class) != null) {
            return new ProductTimeoutException();
        }
        if (findCause(throwable, CallNotPermittedException.class) != null) {
            return new ProductUnavailableException();
        }

        WebClientResponseException webEx = findCause(throwable, WebClientResponseException.class);
        if (webEx != null && webEx.getStatusCode().is5xxServerError()) {
            return new ProductUpstreamException(extractWebClientMessage(webEx));
        }

        FeignException feignEx = findCause(throwable, FeignException.class);
        if (feignEx != null && feignEx.status() >= 500) {
            return new ProductUpstreamException(extractFeignMessage(feignEx));
        }

        Throwable cause = rootCause(throwable);
        return new ProductUpstreamException(
                cause.getMessage() != null ? cause.getMessage() : "Error en el servicio de productos");
    }

    private static <T extends Throwable> T findCause(Throwable throwable, Class<T> type) {
        Throwable current = throwable;
        while (current != null) {
            if (type.isInstance(current)) {
                return type.cast(current);
            }
            current = current.getCause();
        }
        return null;
    }

    private static Throwable rootCause(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private static String extractWebClientMessage(WebClientResponseException ex) {
        String body = ex.getResponseBodyAsString();
        if (body != null && !body.isBlank()) {
            return body;
        }
        return "Error en el servicio de productos";
    }

    private static String extractFeignMessage(FeignException ex) {
        String body = ex.contentUTF8();
        if (body != null && !body.isBlank()) {
            return body;
        }
        return "Error en el servicio de productos";
    }
}
