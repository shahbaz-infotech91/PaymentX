package com.paymentx.controlcenter.dto.postgres;

/**
 * ENGLISH: The fixed, allowlisted set of columns the Transaction
 * Monitor is allowed to sort by. What it does: pairs a stable API
 * name with the one real column name it maps to. Why it exists: JDBC
 * can parameterize values but never identifiers (column/table names) -
 * ORDER BY has to be built from a trusted, fixed string, so this enum
 * IS the SQL-injection boundary for sorting, the same way
 * ServiceIdentifier/PostgresDatabaseIdentifier are the boundary for
 * URLs/database names. An unrecognized sort field quietly falls back
 * to CREATED_AT rather than throwing - sorting is a display
 * preference, not a security-sensitive resource lookup.
 *
 * HINGLISH: Fixed, allowlisted columns ka set jinse Transaction
 * Monitor ko sort karne ki ijazat hai. Ye kya karti hai: ek stable API
 * name ko us ek real column name ke saath pair karta hai jise wo map
 * karta hai. Ye dashboard me kyu hai: JDBC values ko parameterize kar
 * sakta hai lekin identifiers (column/table names) ko kabhi nahi -
 * ORDER BY ko ek trusted, fixed string se banana padta hai, isliye ye
 * enum sorting ke liye SQL-injection boundary HAI, waise hi jaise
 * ServiceIdentifier/PostgresDatabaseIdentifier URLs/database names ke
 * liye boundary hain. Ek unrecognized sort field quietly CREATED_AT
 * par fallback ho jaata hai, throw nahi karta - sorting ek display
 * preference hai, koi security-sensitive resource lookup nahi.
 */
public enum PaymentSortField {
    CREATED_AT("created_at"),
    UPDATED_AT("updated_at"),
    AMOUNT("amount"),
    STATUS("status"),
    PAYMENT_REFERENCE("payment_reference");

    private final String column;

    PaymentSortField(String column) {
        this.column = column;
    }

    public String column() {
        return column;
    }

    public static PaymentSortField fromParam(String value) {
        if (value == null) return CREATED_AT;
        for (PaymentSortField field : values()) {
            if (field.name().equalsIgnoreCase(value)) {
                return field;
            }
        }
        return CREATED_AT;
    }
}
