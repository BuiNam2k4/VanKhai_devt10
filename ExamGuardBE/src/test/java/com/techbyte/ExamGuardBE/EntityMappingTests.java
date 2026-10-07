package com.techbyte.ExamGuardBE;

import com.techbyte.ExamGuardBE.entity.*;
import com.techbyte.ExamGuardBE.enums.RoleName;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class EntityMappingTests {
    @Autowired
    private EntityManager entityManager;

    @Test
    void persistsUnicodeAndManyToManyRelationsWithoutCascadingUsers() {
        Role role = new Role();
        role.setName(RoleName.STUDENT);
        entityManager.persist(role);

        User student = user("student", "Sinh viên Phạm Văn Khải");
        student.getRoles().add(role);
        entityManager.persist(student);
        User teacher = user("teacher", "Giảng viên");
        entityManager.persist(teacher);

        SchoolClass schoolClass = new SchoolClass();
        schoolClass.setCode("DEV-T10");
        schoolClass.setName("Lớp phát triển phần mềm");
        schoolClass.setTeacher(teacher);
        schoolClass.getStudents().add(student);
        entityManager.persist(schoolClass);
        entityManager.flush();
        entityManager.clear();

        SchoolClass loaded = entityManager.find(SchoolClass.class, schoolClass.getId());
        assertThat(loaded.getName()).isEqualTo("Lớp phát triển phần mềm");
        assertThat(loaded.getStudents()).extracting(User::getFullName).containsExactly("Sinh viên Phạm Văn Khải");
        assertThat(loaded.getStudents().iterator().next().getRoles()).extracting(Role::getName).containsExactly(RoleName.STUDENT);
        assertThat(loaded.getCreatedAt()).isNotNull();
        assertThat(loaded.getUpdatedAt()).isNotNull();
        entityManager.remove(loaded);
        entityManager.flush();
        assertThat(entityManager.find(User.class, student.getId())).isNotNull();
        assertThat(entityManager.find(User.class, teacher.getId())).isNotNull();
    }

    private User user(String username, String fullName) {
        User user = new User();
        user.setUsername(username);
        user.setEmail(username + "@example.test");
        user.setPasswordHash("test-only-hash");
        user.setFullName(fullName);
        return user;
    }
}
