package com.rahbar.util;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A column kept in the database but never sent to a screen in the generic rows of {@link Rows} (e.g. sponsorship
 * amounts: everyone sees installments, "installment 2 paid", not money).
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface NotShown {}
