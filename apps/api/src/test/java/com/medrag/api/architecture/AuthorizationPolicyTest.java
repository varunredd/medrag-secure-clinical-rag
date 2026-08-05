package com.medrag.api.architecture;

import com.medrag.api.controller.DocumentController;
import com.medrag.api.controller.ExportRequestController;
import com.medrag.api.controller.OperationsController;
import com.medrag.api.controller.QueryController;
import com.medrag.api.controller.SessionEventController;
import com.medrag.api.controller.TenantSettingController;
import com.medrag.api.controller.UploadPolicyController;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.reflect.Method;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AuthorizationPolicyTest {
    @Test
    void clinicalRoleMatrixIsExplicit() throws Exception {
        assertPolicy(
                DocumentController.class.getMethod(
                        "upload",
                        org.springframework.web.multipart.MultipartFile.class
                ),
                "hasAnyRole('DOCTOR','NURSE','CLINIC_ADMIN')"
        );
        assertPolicy(
                DocumentController.class.getMethod("list", int.class, int.class),
                "hasAnyRole('DOCTOR','NURSE','CLINIC_ADMIN','AUDITOR')"
        );
        assertPolicy(
                DocumentController.class.getMethod("get", UUID.class),
                "hasAnyRole('DOCTOR','NURSE','CLINIC_ADMIN','AUDITOR')"
        );
        assertPolicy(
                QueryController.class.getMethod("query", QueryController.Request.class),
                "hasAnyRole('DOCTOR','NURSE')"
        );
        assertPolicy(
                DocumentController.class.getMethod("retry", UUID.class),
                "hasAnyRole('DOCTOR','CLINIC_ADMIN')"
        );
    }

    @Test
    void governanceMutationsAreClinicAdminOnly() throws Exception {
        assertPolicy(
                DocumentController.class.getMethod("delete", UUID.class),
                "hasRole('CLINIC_ADMIN')"
        );
        assertPolicy(
                DocumentController.class.getMethod(
                        "legalHold", UUID.class, DocumentController.LegalHoldRequest.class
                ),
                "hasRole('CLINIC_ADMIN')"
        );
        assertPolicy(
                DocumentController.class.getMethod(
                        "classification", UUID.class, DocumentController.ClassificationRequest.class
                ),
                "hasRole('CLINIC_ADMIN')"
        );
        assertPolicy(
                OperationsController.class.getMethod("redrive", UUID.class),
                "hasRole('CLINIC_ADMIN')"
        );
        assertPolicy(
                TenantSettingController.class.getMethod("get"),
                "hasRole('CLINIC_ADMIN')"
        );
        assertPolicy(
                TenantSettingController.class.getMethod(
                        "update", TenantSettingController.Request.class
                ),
                "hasRole('CLINIC_ADMIN')"
        );
        assertPolicy(
                ExportRequestController.class.getMethod(
                        "create", ExportRequestController.CreateRequest.class
                ),
                "hasRole('CLINIC_ADMIN')"
        );
    }

    @Test
    void auditorReceivesReadOnlyOperationalAndComplianceAccess() throws Exception {
        assertPolicy(
                OperationsController.class.getMethod("overview"),
                "hasAnyRole('DOCTOR','NURSE','CLINIC_ADMIN','AUDITOR')"
        );
        assertPolicy(
                UploadPolicyController.class.getMethod("get"),
                "hasAnyRole('DOCTOR','NURSE','CLINIC_ADMIN','AUDITOR')"
        );
        assertPolicy(
                ExportRequestController.class.getMethod("list", int.class, int.class),
                "hasAnyRole('CLINIC_ADMIN','AUDITOR')"
        );
        assertPolicy(
                SessionEventController.class.getMethod("record", SessionEventController.Request.class),
                "hasAnyRole('DOCTOR','NURSE','CLINIC_ADMIN','AUDITOR')"
        );
    }

    private static void assertPolicy(Method method, String expected) {
        PreAuthorize annotation = method.getAnnotation(PreAuthorize.class);
        assertThat(annotation).as("@PreAuthorize on %s", method).isNotNull();
        assertThat(annotation.value()).isEqualTo(expected);
    }
}
