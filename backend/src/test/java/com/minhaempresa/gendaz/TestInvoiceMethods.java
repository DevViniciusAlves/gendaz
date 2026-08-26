package com.minhaempresa.gendaz;

import com.stripe.model.Invoice;
import java.lang.reflect.Method;

public class TestInvoiceMethods {
    public static void main(String[] args) {
        for (Method method : Invoice.class.getMethods()) {
            System.out.println("Method: " + method.getName());
        }
    }
}
