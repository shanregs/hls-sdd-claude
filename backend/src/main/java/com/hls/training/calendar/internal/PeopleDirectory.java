package com.hls.training.calendar.internal;

import com.hls.organization.api.ManagerQueries;
import com.hls.organization.api.ManagerQueries.ManagerRef;
import com.hls.teacher.api.TeacherDirectory;
import com.hls.teacher.api.TeacherDirectory.TeacherInfo;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Names, statuses and user accounts of Teachers and Managers, read in bulk through the public interfaces of their modules. */
@Component
public class PeopleDirectory {

    /** A person of either kind. {@code status} is the Teacher status, or ACTIVE or INACTIVE for a Manager. */
    public record Person(String kind, UUID id, String name, UUID userId, String status) {

        public boolean active() {
            return "ACTIVE".equals(status);
        }
    }

    private final TeacherDirectory teachers;
    private final ManagerQueries managers;

    PeopleDirectory(TeacherDirectory teachers, ManagerQueries managers) {
        this.teachers = teachers;
        this.managers = managers;
    }

    public Map<UUID, Person> teachers(Collection<UUID> ids) {
        Map<UUID, Person> out = new HashMap<>();
        if (ids.isEmpty()) {
            return out;
        }
        for (TeacherInfo info : teachers.teacherInfo(ids).values()) {
            out.put(info.id(), new Person(Assignment.TEACHER, info.id(), info.name(), info.userId(), info.status()));
        }
        return out;
    }

    public Map<UUID, Person> managers(Collection<UUID> ids) {
        Map<UUID, Person> out = new HashMap<>();
        if (ids.isEmpty()) {
            return out;
        }
        Set<UUID> wanted = new HashSet<>(ids);
        for (ManagerRef ref : managers.allManagers()) {
            if (wanted.contains(ref.id())) {
                out.put(ref.id(), new Person(Assignment.MANAGER, ref.id(), ref.displayName(), ref.userId(), ref.active() ? "ACTIVE" : "INACTIVE"));
            }
        }
        return out;
    }

    /** People of both kinds keyed by id (ids of a Teacher and a Manager never collide). */
    public Map<UUID, Person> people(Collection<UUID> teacherIds, Collection<UUID> managerIds) {
        Map<UUID, Person> out = teachers(teacherIds);
        out.putAll(managers(managerIds));
        return out;
    }

    public Person person(String kind, UUID id) {
        Map<UUID, Person> found = Assignment.MANAGER.equals(kind) ? managers(List.of(id)) : teachers(List.of(id));
        return found.get(id);
    }

    public String nameOf(String kind, UUID id) {
        Person person = person(kind, id);
        return person == null ? "Unknown" : person.name();
    }

    /** The user accounts of the given assignments' people and Teachers, skipping people without an account. */
    public Set<UUID> userIds(Collection<UUID> teacherIds, Collection<UUID> managerIds) {
        Set<UUID> users = new HashSet<>();
        people(teacherIds, managerIds).values().forEach(p -> {
            if (p.userId() != null) {
                users.add(p.userId());
            }
        });
        return users;
    }
}
