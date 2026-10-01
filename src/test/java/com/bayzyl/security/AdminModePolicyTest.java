package com.bayzyl.security;

import com.bayzyl.AdminModeService;
import com.bayzyl.BuilderProfileConfig;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class AdminModePolicyTest {
    @Test
    void enabledSessionBecomesIneffectiveWhenPermissionIsRevoked() {
        UUID playerId = UUID.randomUUID();
        AdminModeService service = new AdminModeService();
        service.setEnabled(playerId, true);

        assertTrue(service.isActive(playerId, permission -> true));
        assertFalse(service.isActive(playerId, permission -> false));
    }

    @Test
    void reconnectAlwaysBeginsWithAdminModeDisabled() {
        UUID playerId = UUID.randomUUID();
        AdminModeService service = new AdminModeService();
        service.setEnabled(playerId, true);

        service.beginSession(playerId);

        assertFalse(service.isEnabled(playerId));
    }

    @Test
    void profileContractCannotCarryLegacyAdminMode() {
        assertFalse(Arrays.stream(BuilderProfileConfig.class.getRecordComponents())
                .anyMatch(component -> component.getName().equals("adminModeEnabled")));
    }
}
