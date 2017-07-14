package com.tngtech.archunit.lang.extension.examples;

import java.util.Properties;

import com.tngtech.archunit.lang.extension.ArchUnitExtension;
import com.tngtech.archunit.lang.extension.EvaluatedRule;

public class DummyTestExtension implements ArchUnitExtension {
    @Override
    public String getUniqueIdentifier() {
        return getClass().getName();
    }

    @Override
    public void configure(Properties properties) {
    }

    @Override
    public void handle(EvaluatedRule evaluatedRule) {
    }
}
