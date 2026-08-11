package com.minhaempresa.gendaz.shared;

public final class CompanyContext {
    private static final ThreadLocal<Long> CURRENT_COMPANY = new ThreadLocal<>();

    private CompanyContext() {}

    public static void setCompanyId(Long companyId) {
        if (companyId == null) {
            CURRENT_COMPANY.remove();
            return;
        }
        CURRENT_COMPANY.set(companyId);
    }

    public static Long getCompanyId() {
        return CURRENT_COMPANY.get();
    }

    public static boolean isSet() {
        return CURRENT_COMPANY.get() != null;
    }

    public static void clear() {
        CURRENT_COMPANY.remove();
    }
}

