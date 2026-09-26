package com.example.myapplication.util;

import android.content.Context;
import android.content.SharedPreferences;

/** Tiny SharedPreferences-backed session store for the logged-in role/user. */
public class SessionManager {

    private static final String PREFS = "session_prefs";
    private static final String KEY_ROLE = "role";
    private static final String KEY_STAFF_EMPLOYEE_ID = "staff_employee_id";

    public static final String ROLE_ADMIN = "ADMIN";
    public static final String ROLE_STAFF = "STAFF";

    /** Employee ID used to seed/lookup the Staff record tied to the hardcoded staff login. */
    public static final String DEMO_STAFF_EMPLOYEE_ID = "EMP001";
    public static final String DEMO_STAFF_NAME = "Demo Staff";

    private final SharedPreferences prefs;

    public SessionManager(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public void loginAsAdmin() {
        prefs.edit().putString(KEY_ROLE, ROLE_ADMIN).apply();
    }

    public void loginAsStaff(String employeeId) {
        prefs.edit()
                .putString(KEY_ROLE, ROLE_STAFF)
                .putString(KEY_STAFF_EMPLOYEE_ID, employeeId)
                .apply();
    }

    public String getRole() {
        return prefs.getString(KEY_ROLE, null);
    }

    public String getLoggedInStaffEmployeeId() {
        return prefs.getString(KEY_STAFF_EMPLOYEE_ID, DEMO_STAFF_EMPLOYEE_ID);
    }

    public void logout() {
        prefs.edit().clear().apply();
    }
}
