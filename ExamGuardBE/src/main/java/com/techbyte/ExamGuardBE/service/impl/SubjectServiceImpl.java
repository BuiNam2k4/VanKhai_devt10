package com.techbyte.ExamGuardBE.service.impl;

import com.techbyte.ExamGuardBE.common.PaginationUtils;
import com.techbyte.ExamGuardBE.controller.request.SubjectRequest;
import com.techbyte.ExamGuardBE.controller.response.*;
import com.techbyte.ExamGuardBE.entity.Subject;
import com.techbyte.ExamGuardBE.exception.*;
import com.techbyte.ExamGuardBE.mapper.ManagementMapper;
import com.techbyte.ExamGuardBE.repository.SubjectRepository;
import com.techbyte.ExamGuardBE.service.SubjectService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j(topic = "SUBJECT-SERVICE")
public class SubjectServiceImpl implements SubjectService {
    private static final Set<String> SORT_FIELDS = Set.of("id", "code", "name", "createdAt");
    private final SubjectRepository subjectRepository;

    @Override
    @Transactional(readOnly = true)
    public SubjectPageResponse findAll(String keyword, String sortBy, int page, int size) {
        var pageable = PaginationUtils.pageable(page, size, sortBy, SORT_FIELDS);
        String search = keyword == null || keyword.isBlank() ? "" : "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%";
        Page<Subject> result = subjectRepository.search(search, pageable);
        SubjectPageResponse response = new SubjectPageResponse();
        response.setSubjects(result.getContent().stream().map(ManagementMapper::toSubjectResponse).toList());
        PaginationUtils.fill(response, page, size, result);
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public SubjectResponse findById(Long id) {
        return ManagementMapper.toSubjectResponse(getSubject(id));
    }

    @Override
    @Transactional
    public SubjectResponse save(SubjectRequest request) {
        String code = normalizeCode(request.getCode());
        if (subjectRepository.existsByCodeIgnoreCase(code)) throw new AppException(ErrorCode.SUBJECT_EXISTS);
        Subject subject = new Subject();
        copy(request, subject, code);
        log.info("Creating subject code={}", code);
        return ManagementMapper.toSubjectResponse(subjectRepository.saveAndFlush(subject));
    }

    @Override
    @Transactional
    public SubjectResponse update(Long id, SubjectRequest request) {
        Subject subject = getSubject(id);
        String code = normalizeCode(request.getCode());
        if (subjectRepository.existsByCodeIgnoreCaseAndIdNot(code, id)) throw new AppException(ErrorCode.SUBJECT_EXISTS);
        copy(request, subject, code);
        log.info("Updating subject id={}", id);
        return ManagementMapper.toSubjectResponse(subjectRepository.saveAndFlush(subject));
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Subject subject = getSubject(id);
        log.info("Deleting subject id={}", id);
        subjectRepository.delete(subject);
        subjectRepository.flush();
    }

    private Subject getSubject(Long id) {
        return subjectRepository.findById(id).orElseThrow(() -> new AppException(ErrorCode.SUBJECT_NOT_FOUND));
    }

    private static void copy(SubjectRequest request, Subject subject, String code) {
        subject.setCode(code);
        subject.setName(request.getName().trim());
        subject.setDescription(request.getDescription() == null || request.getDescription().isBlank()
                ? null : request.getDescription().trim());
    }

    private static String normalizeCode(String code) {
        return code.trim().toUpperCase(Locale.ROOT);
    }
}
