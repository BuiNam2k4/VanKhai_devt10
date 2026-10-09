package com.techbyte.ExamGuardBE;

import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "EXAMGUARD_MYSQL_TEST", matches = "true")
class MySqlAuthManagementIntegrationTests extends AbstractAuthManagementIntegrationTests {}
