package com.smartprep.dto.request;

import lombok.Data;

/**
 * Admin change to an account: either field may be sent on its own.
 * role is "STUDENT" or "ADMIN"; suspended blocks login and ends the current session.
 */
@Data
public class AdminUserUpdateRequest {
    private String role;
    private Boolean suspended;
}
