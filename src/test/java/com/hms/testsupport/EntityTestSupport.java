package com.hms.testsupport;

import com.hms.tenancy.TenantEntity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;

import java.lang.reflect.Field;
import java.util.stream.Stream;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Shared reflection helper for giving a detached test entity an id, since TenantEntity has no public setter for it. */
public final class EntityTestSupport {

    private EntityTestSupport() {
    }

    public static <T extends TenantEntity> T withId(T entity, Long id) {
        try {
            Field idField = TenantEntity.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(entity, id);
            return entity;
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    /** For mutating a field the entity's real API deliberately doesn't expose a setter for (e.g. status, only ever changed via service-layer rules). */
    public static void setField(Object target, Class<?> declaringClass, String fieldName, Object value) {
        try {
            Field field = declaringClass.getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Mocks the JPQL query chain {@code TenantScoping.findByIdTenantScoped}
     * runs under the hood, so a mocked {@code EntityManager} returns
     * {@code result} for a lookup of the given type/id — the entity-manager
     * analogue of {@code when(repository.findById(id)).thenReturn(...)},
     * needed because that helper deliberately doesn't go through
     * {@code EntityManager.find()} (see its Javadoc for why). Pass a null
     * {@code result} to mock the not-found case.
     */
    @SuppressWarnings("unchecked")
    public static <T> void mockTenantScopedFind(EntityManager entityManager, Class<T> type, Long id, T result) {
        TypedQuery<T> query = mock(TypedQuery.class);
        when(entityManager.createQuery(anyString(), eq(type))).thenReturn(query);
        when(query.setParameter(eq("id"), eq(id))).thenReturn(query);
        when(query.getResultStream()).thenReturn(result == null ? Stream.empty() : Stream.of(result));
    }
}
