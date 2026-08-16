package com.documind.document.demo;

import com.documind.common.domain.UserRole;
import com.documind.common.domain.WorkspacePlan;
import com.documind.common.persistence.entity.UserEntity;
import com.documind.common.persistence.entity.WorkspaceEntity;
import com.documind.common.persistence.repository.UserRepository;
import com.documind.common.persistence.repository.WorkspaceRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DemoWorkspaceRegistrar {

    private final WorkspaceRepository workspaceRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public DemoWorkspaceRegistrar(
            WorkspaceRepository workspaceRepository,
            UserRepository userRepository,
            PasswordEncoder passwordEncoder) {
        this.workspaceRepository = workspaceRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public UserEntity createWorkspaceOwner(String workspaceName, String email, String password) {
        Instant now = Instant.now();
        WorkspaceEntity workspace =
                workspaceRepository.save(
                        new WorkspaceEntity(
                                UUID.randomUUID(), workspaceName, WorkspacePlan.TEAM, now));

        return userRepository.save(
                new UserEntity(
                        UUID.randomUUID(),
                        email,
                        passwordEncoder.encode(password),
                        workspace.getId(),
                        UserRole.ADMIN,
                        now));
    }
}
