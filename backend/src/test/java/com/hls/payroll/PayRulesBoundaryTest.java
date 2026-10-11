package com.hls.payroll;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.payroll.web.PayPolicyController;
import com.hls.payroll.web.SalaryStructureController;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

/**
 * Spec 013a FR-016, FR-017 (T032): the pay rules are server-side only, and no payroll endpoint or screen model can carry
 * an individual person's pay.
 */
class PayRulesBoundaryTest {

    /** Field names that would identify a person, or hold one person's pay. */
    private static final Pattern PERSONAL = Pattern.compile(
            "(?i).*(teacherId|managerId|personId|employeeId|userId|monthlySalary|payslip|lossOfPay|netPay|deduction).*");

    private static List<Method> endpoints(Class<?> controller) {
        List<Method> endpoints = new ArrayList<>();
        for (Method method : controller.getDeclaredMethods()) {
            if (method.isAnnotationPresent(GetMapping.class) || method.isAnnotationPresent(PostMapping.class)) {
                endpoints.add(method);
            }
        }
        return endpoints;
    }

    private static void collect(Type type, Set<Class<?>> seen, Set<String> fieldNames, Set<Class<?>> classes) {
        if (type instanceof ParameterizedType parameterized) {
            collect(parameterized.getRawType(), seen, fieldNames, classes);
            for (Type argument : parameterized.getActualTypeArguments()) {
                collect(argument, seen, fieldNames, classes);
            }
            return;
        }
        if (!(type instanceof Class<?> clazz) || !seen.add(clazz)) {
            return;
        }
        classes.add(clazz);
        if (clazz.isRecord()) {
            for (RecordComponent component : clazz.getRecordComponents()) {
                fieldNames.add(component.getName());
                collect(component.getGenericType(), seen, fieldNames, classes);
            }
        }
    }

    private static void inspectResponses(Set<String> fieldNames, Set<Class<?>> classes) {
        Set<Class<?>> seen = new HashSet<>();
        for (Class<?> controller : List.of(SalaryStructureController.class, PayPolicyController.class)) {
            for (Method endpoint : endpoints(controller)) {
                collect(endpoint.getGenericReturnType(), seen, fieldNames, classes);
            }
        }
    }

    @Test
    void thereAreFiveEndpointsAndNoneReturnsAPayRulesResult() {
        assertThat(endpoints(SalaryStructureController.class)).hasSize(3);
        assertThat(endpoints(PayPolicyController.class)).hasSize(2);
        Set<String> names = new HashSet<>();
        Set<Class<?>> classes = new HashSet<>();
        inspectResponses(names, classes);
        assertThat(classes).isNotEmpty();
        // the policy's own enums are shared with the screen, but no PayRules answer type is ever returned over HTTP
        Set<String> payRulesAnswers = Set.of(
                "PayRules", "SalaryInEffect", "LossOfPayValue", "PayableDays", "Span", "UnpaidDays", "UnpaidDay", "TotalLossOfPay");
        assertThat(classes)
                .noneMatch(c -> c.getPackageName().equals("com.hls.payroll.api")
                        && payRulesAnswers.contains(c.getSimpleName()));
    }

    @Test
    void noResponseCarriesAPersonIdOrAnIndividualsPay() {
        Set<String> names = new HashSet<>();
        Set<Class<?>> classes = new HashSet<>();
        inspectResponses(names, classes);
        assertThat(names).isNotEmpty().noneMatch(name -> PERSONAL.matcher(name).matches());
    }

    @Test
    void theTeacherViewStillHasNoSalaryOrPayMember() throws ClassNotFoundException {
        Class<?> view = Class.forName("com.hls.teacher.api.TeacherView");
        List<String> members = new ArrayList<>();
        for (java.lang.reflect.Field field : view.getDeclaredFields()) {
            members.add(field.getName().toLowerCase());
        }
        for (Method method : view.getDeclaredMethods()) {
            members.add(method.getName().toLowerCase());
        }
        assertThat(members).isNotEmpty().noneMatch(m -> m.contains("salary") || m.contains("pay"));
    }
}
