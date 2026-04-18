package com.bt.deliveryapp.enums;

public enum RecurrenceFrequency {
    DAILY,
    EVERY_X_DAYS,
    SPECIFIC_DAYS_OF_WEEK,
    WEEKLY,
    EVERY_2_WEEKS,
    EVERY_3_WEEKS,
    MONTHLY,
    SPECIFIC_DATE_OF_MONTH;

    public String getDisplayName() {
        switch (this) {
            case DAILY:                  return "Every Day";
            case EVERY_X_DAYS:           return "Every X Days";
            case SPECIFIC_DAYS_OF_WEEK:  return "Specific Days of the Week";
            case WEEKLY:                 return "Every Week";
            case EVERY_2_WEEKS:          return "Every 2 Weeks";
            case EVERY_3_WEEKS:          return "Every 3 Weeks";
            case MONTHLY:                return "Every Month";
            case SPECIFIC_DATE_OF_MONTH: return "Specific Date of Month";
            default:                     return this.name();
        }
    }
}
