package com.sysadmindoc.billminder.data

data class BillTemplate(
    val name: String,
    val category: BillCategory,
    val suggestedAmount: Double?,
    val recurrence: Recurrence = Recurrence.MONTHLY
)

val billTemplates = listOf(
    BillTemplate("Rent", BillCategory.RENT, null),
    BillTemplate("Mortgage", BillCategory.RENT, null),
    BillTemplate("Electric", BillCategory.UTILITIES, null),
    BillTemplate("Gas", BillCategory.UTILITIES, null),
    BillTemplate("Water/Sewer", BillCategory.UTILITIES, null),
    BillTemplate("Trash", BillCategory.UTILITIES, null),
    BillTemplate("Internet", BillCategory.PHONE, null),
    BillTemplate("Cell Phone", BillCategory.PHONE, null),
    BillTemplate("Car Insurance", BillCategory.INSURANCE, null),
    BillTemplate("Health Insurance", BillCategory.INSURANCE, null),
    BillTemplate("Renters Insurance", BillCategory.INSURANCE, null, Recurrence.MONTHLY),
    BillTemplate("Car Payment", BillCategory.LOAN, null),
    BillTemplate("Student Loan", BillCategory.LOAN, null),
    BillTemplate("Credit Card", BillCategory.LOAN, null),
    BillTemplate("Netflix", BillCategory.SUBSCRIPTION, 15.49),
    BillTemplate("Spotify", BillCategory.SUBSCRIPTION, 11.99),
    BillTemplate("YouTube Premium", BillCategory.SUBSCRIPTION, 13.99),
    BillTemplate("Disney+", BillCategory.SUBSCRIPTION, 13.99),
    BillTemplate("Hulu", BillCategory.SUBSCRIPTION, 17.99),
    BillTemplate("Amazon Prime", BillCategory.SUBSCRIPTION, 14.99),
    BillTemplate("Apple One", BillCategory.SUBSCRIPTION, 19.95),
    BillTemplate("iCloud+", BillCategory.SUBSCRIPTION, 2.99),
    BillTemplate("Google One", BillCategory.SUBSCRIPTION, 2.99),
    BillTemplate("Xbox Game Pass", BillCategory.SUBSCRIPTION, 17.99),
    BillTemplate("PlayStation Plus", BillCategory.SUBSCRIPTION, 17.99, Recurrence.MONTHLY),
    BillTemplate("Gym Membership", BillCategory.SUBSCRIPTION, null),
    BillTemplate("Daycare", BillCategory.CHILDCARE, null),
    BillTemplate("Groceries", BillCategory.GROCERIES, null, Recurrence.WEEKLY),
)
