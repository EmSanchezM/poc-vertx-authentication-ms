package com.auth.microservice.application.query;

import com.auth.microservice.domain.model.Email;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Authentication Query Tests")
class AuthQueryTest {

    @Nested
    @DisplayName("FindUserByEmailQuery Tests")
    class FindUserByEmailQueryTests {

        @Test
        @DisplayName("Should create query with all parameters")
        void shouldCreateQueryWithAllParameters() {
            // Given
            String userId = "user-123";
            Email email = new Email("test@example.com");
            boolean includeRoles = true;
            boolean includePermissions = true;

            // When
            FindUserByEmailQuery query = new FindUserByEmailQuery(userId, email, includeRoles, includePermissions);

            // Then
            assertEquals(userId, query.getUserId());
            assertEquals(email, query.getEmail());
            assertTrue(query.isIncludeRoles());
            assertTrue(query.isIncludePermissions());
            assertNotNull(query.getQueryId());
            assertNotNull(query.getTimestamp());
        }

        @Test
        @DisplayName("Should create query with convenience constructor")
        void shouldCreateQueryWithConvenienceConstructor() {
            // Given
            String userId = "user-123";
            Email email = new Email("test@example.com");

            // When
            FindUserByEmailQuery query = new FindUserByEmailQuery(userId, email);

            // Then
            assertEquals(userId, query.getUserId());
            assertEquals(email, query.getEmail());
            assertFalse(query.isIncludeRoles());
            assertFalse(query.isIncludePermissions());
        }

        @Test
        @DisplayName("Should have meaningful toString")
        void shouldHaveMeaningfulToString() {
            // Given
            String userId = "user-123";
            Email email = new Email("test@example.com");
            FindUserByEmailQuery query = new FindUserByEmailQuery(userId, email, true, false);

            // When
            String toString = query.toString();

            // Then
            assertTrue(toString.contains("FindUserByEmailQuery"));
            assertTrue(toString.contains(email.toString()));
            assertTrue(toString.contains("includeRoles=true"));
            assertTrue(toString.contains("includePermissions=false"));
        }
    }

    @Nested
    @DisplayName("GetUserPermissionsQuery Tests")
    class GetUserPermissionsQueryTests {

        @Test
        @DisplayName("Should create query with all parameters")
        void shouldCreateQueryWithAllParameters() {
            // Given
            String userId = "user-123";
            UUID targetUserId = UUID.randomUUID();
            boolean includeInherited = false;
            boolean useCache = false;

            // When
            GetUserPermissionsQuery query = new GetUserPermissionsQuery(userId, targetUserId, includeInherited, useCache);

            // Then
            assertEquals(userId, query.getUserId());
            assertEquals(targetUserId, query.getTargetUserId());
            assertFalse(query.isIncludeInherited());
            assertFalse(query.isUseCache());
            assertNotNull(query.getQueryId());
            assertNotNull(query.getTimestamp());
        }

        @Test
        @DisplayName("Should create query with convenience constructor")
        void shouldCreateQueryWithConvenienceConstructor() {
            // Given
            String userId = "user-123";
            UUID targetUserId = UUID.randomUUID();

            // When
            GetUserPermissionsQuery query = new GetUserPermissionsQuery(userId, targetUserId);

            // Then
            assertEquals(userId, query.getUserId());
            assertEquals(targetUserId, query.getTargetUserId());
            assertTrue(query.isIncludeInherited());
            assertTrue(query.isUseCache());
        }

        @Test
        @DisplayName("Should have meaningful toString")
        void shouldHaveMeaningfulToString() {
            // Given
            String userId = "user-123";
            UUID targetUserId = UUID.randomUUID();
            GetUserPermissionsQuery query = new GetUserPermissionsQuery(userId, targetUserId, false, true);

            // When
            String toString = query.toString();

            // Then
            assertTrue(toString.contains("GetUserPermissionsQuery"));
            assertTrue(toString.contains(targetUserId.toString()));
            assertTrue(toString.contains("includeInherited=false"));
            assertTrue(toString.contains("useCache=true"));
        }
    }

    @Nested
    @DisplayName("CheckPermissionQuery Tests")
    class CheckPermissionQueryTests {

        @Test
        @DisplayName("Should create query for permission name check")
        void shouldCreateQueryForPermissionNameCheck() {
            // Given
            String userId = "user-123";
            UUID targetUserId = UUID.randomUUID();
            String permissionName = "READ_USER";
            boolean useCache = true;

            // When
            CheckPermissionQuery query = new CheckPermissionQuery(userId, targetUserId, permissionName, useCache);

            // Then
            assertEquals(userId, query.getUserId());
            assertEquals(targetUserId, query.getTargetUserId());
            assertEquals(permissionName, query.getPermissionName());
            assertNull(query.getResource());
            assertNull(query.getAction());
            assertTrue(query.isUseCache());
            assertTrue(query.isCheckByName());
            assertFalse(query.isCheckByResourceAction());
        }

        @Test
        @DisplayName("Should create query for resource and action check")
        void shouldCreateQueryForResourceAndActionCheck() {
            // Given
            String userId = "user-123";
            UUID targetUserId = UUID.randomUUID();
            String resource = "user";
            String action = "read";
            boolean useCache = false;

            // When
            CheckPermissionQuery query = new CheckPermissionQuery(userId, targetUserId, resource, action, useCache);

            // Then
            assertEquals(userId, query.getUserId());
            assertEquals(targetUserId, query.getTargetUserId());
            assertNull(query.getPermissionName());
            assertEquals(resource, query.getResource());
            assertEquals(action, query.getAction());
            assertFalse(query.isUseCache());
            assertFalse(query.isCheckByName());
            assertTrue(query.isCheckByResourceAction());
        }

        @Test
        @DisplayName("Should create query with convenience constructor for permission name")
        void shouldCreateQueryWithConvenienceConstructorForPermissionName() {
            // Given
            String userId = "user-123";
            UUID targetUserId = UUID.randomUUID();
            String permissionName = "READ_USER";

            // When
            CheckPermissionQuery query = new CheckPermissionQuery(userId, targetUserId, permissionName);

            // Then
            assertEquals(userId, query.getUserId());
            assertEquals(targetUserId, query.getTargetUserId());
            assertEquals(permissionName, query.getPermissionName());
            assertTrue(query.isUseCache());
            assertTrue(query.isCheckByName());
        }

        @Test
        @DisplayName("Should create query with convenience constructor for resource and action")
        void shouldCreateQueryWithConvenienceConstructorForResourceAndAction() {
            // Given
            String userId = "user-123";
            UUID targetUserId = UUID.randomUUID();
            String resource = "user";
            String action = "read";

            // When
            CheckPermissionQuery query = new CheckPermissionQuery(userId, targetUserId, resource, action);

            // Then
            assertEquals(userId, query.getUserId());
            assertEquals(targetUserId, query.getTargetUserId());
            assertEquals(resource, query.getResource());
            assertEquals(action, query.getAction());
            assertTrue(query.isUseCache());
            assertTrue(query.isCheckByResourceAction());
        }

        @Test
        @DisplayName("Should have meaningful toString for permission name check")
        void shouldHaveMeaningfulToStringForPermissionNameCheck() {
            // Given
            String userId = "user-123";
            UUID targetUserId = UUID.randomUUID();
            String permissionName = "READ_USER";
            CheckPermissionQuery query = new CheckPermissionQuery(userId, targetUserId, permissionName, false);

            // When
            String toString = query.toString();

            // Then
            assertTrue(toString.contains("CheckPermissionQuery"));
            assertTrue(toString.contains(targetUserId.toString()));
            assertTrue(toString.contains("permissionName='READ_USER'"));
            assertTrue(toString.contains("useCache=false"));
        }

        @Test
        @DisplayName("Should have meaningful toString for resource and action check")
        void shouldHaveMeaningfulToStringForResourceAndActionCheck() {
            // Given
            String userId = "user-123";
            UUID targetUserId = UUID.randomUUID();
            String resource = "user";
            String action = "read";
            CheckPermissionQuery query = new CheckPermissionQuery(userId, targetUserId, resource, action, true);

            // When
            String toString = query.toString();

            // Then
            assertTrue(toString.contains("CheckPermissionQuery"));
            assertTrue(toString.contains(targetUserId.toString()));
            assertTrue(toString.contains("resource='user'"));
            assertTrue(toString.contains("action='read'"));
            assertTrue(toString.contains("useCache=true"));
        }
    }
}