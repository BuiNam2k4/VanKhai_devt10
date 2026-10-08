package com.techbyte.ExamGuardBE;

import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/** Opt-in tests against configured MySQL; all fixture changes are rolled back. */
@EnabledIfEnvironmentVariable(named = "EXAMGUARD_MYSQL_TEST", matches = "true")
class MySqlAuthIntegrationTests extends AuthIntegrationTests {}
