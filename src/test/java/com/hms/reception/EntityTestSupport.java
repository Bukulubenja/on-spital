package com.hms.reception;

import com.hms.tenancy.TenantEntity;

import java.lang.reflect.Field;

/** Shared reflection helper for giving a detached test entity an id, since TenantEntity has no public setter for it. */
final class EntityTestSupport {

    private EntityTestSupport() {
    }

    static <T extends TenantEntity> T withId(T entity, Long id) {
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
    static void setField(Object target, Class<?> declaringClass, String fieldName, Object value) {
        try {
            Field field = declaringClass.getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
