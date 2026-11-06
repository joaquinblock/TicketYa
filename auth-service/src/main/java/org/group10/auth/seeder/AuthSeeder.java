package org.group10.auth.seeder;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.group10.auth.model.Permission;
import org.group10.auth.model.Role;
import org.group10.auth.model.User;
import org.group10.auth.repository.PermissionRepository;
import org.group10.auth.repository.RoleRepository;
import org.group10.auth.repository.UserRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class AuthSeeder implements ApplicationRunner {

    private final PermissionRepository permissionRepository;
    private final RoleRepository roleRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (roleRepository.count() > 0) {
            log.info("AuthSeeder: ya hay datos, se omite la carga");
            return;
        }

        Map<String, Permission> permissions = seedPermissions();
        Map<String, Role> roles = seedRoles(permissions);
        seedUsers(roles);

        log.info("AuthSeeder: {} permisos, {} roles y {} usuarios cargados",
                permissions.size(), roles.size(), userRepository.count());
    }

    private Map<String, Permission> seedPermissions() {
        List<Permission> permissions = List.of(
                permission("EVENT_SEARCH", "Buscar y ver eventos"),
                permission("TICKET_PURCHASE", "Comprar entradas"),
                permission("REFUND_REQUEST", "Solicitar la devolución de una entrada"),
                permission("EVENT_MANAGE", "Crear, editar y publicar eventos propios"),
                permission("REPORT_VIEW", "Ver reportes de eventos propios"),
                permission("EVENT_ADMIN", "Suspender o cancelar cualquier evento"),
                permission("REFUND_RESOLVE", "Aprobar o rechazar devoluciones"),
                permission("USER_MANAGE", "Gestionar usuarios y roles"),
                permission("TICKET_VALIDATE", "Validar entradas en el ingreso")
        );
        return permissionRepository.saveAll(permissions).stream()
                .collect(Collectors.toMap(Permission::getName, Function.identity()));
    }

    private Map<String, Role> seedRoles(Map<String, Permission> p) {
        Set<Permission> buyer = Set.of(p.get("EVENT_SEARCH"), p.get("TICKET_PURCHASE"), p.get("REFUND_REQUEST"));

        List<Role> roles = List.of(
                role("CLIENT", "Busca eventos y compra entradas", buyer),
                role("ORGANIZER", "Diseña y publica eventos",
                        union(buyer, p.get("EVENT_MANAGE"), p.get("REPORT_VIEW"))),
                role("ADMIN", "Gestión completa y tareas operativas",
                        union(buyer, p.get("EVENT_ADMIN"), p.get("REFUND_RESOLVE"), p.get("USER_MANAGE"))),
                role("ACCESS_STAFF", "Escanea el QR de las entradas en el ingreso",
                        union(buyer, p.get("TICKET_VALIDATE")))
        );
        return roleRepository.saveAll(roles).stream()
                .collect(Collectors.toMap(Role::getName, Function.identity()));
    }

    private void seedUsers(Map<String, Role> roles) {
        userRepository.saveAll(List.of(
                user("client@ticketya.com", "Juan", "Pérez", "30111222", "Tandil", roles.get("CLIENT")),
                user("organizer@ticketya.com", "María", "Gómez", "28333444", "Buenos Aires", roles.get("ORGANIZER")),
                user("admin@ticketya.com", "Laura", "Fernández", "25555666", "Tandil", roles.get("ADMIN")),
                user("staff@ticketya.com", "Diego", "López", "35777888", "Mar del Plata", roles.get("ACCESS_STAFF"))
        ));
    }

    private static Permission permission(String name, String description) {
        return Permission.builder().name(name).description(description).build();
    }

    private static Role role(String name, String description, Set<Permission> permissions) {
        return Role.builder().name(name).description(description).permissions(new HashSet<>(permissions)).build();
    }

    private static User user(String email, String firstName, String lastName, String dni, String city, Role role) {
        return User.builder()
                .email(email)
                .firstName(firstName)
                .lastName(lastName)
                .dni(dni)
                .birthDate(LocalDate.of(1995, 1, 1))
                .city(city)
                .role(role)
                .build();
    }

    private static Set<Permission> union(Set<Permission> base, Permission... extra) {
        Set<Permission> result = new HashSet<>(base);
        result.addAll(List.of(extra));
        return result;
    }
}
