package io.github.yangorbunov.incidents.reference;

import java.util.List;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** DTO справочников. Код в запросах не передаётся — его присваивает система. */
public final class ReferenceDtos {

    private ReferenceDtos() {
    }

    public enum Phase { NEW, ACTIVE, RESOLVED, CLOSED }

    // --- Отдел ---
    public record Department(long id, String code, String name, String description, String location) {
    }

    public record DepartmentRequest(@NotBlank @Size(max = 255) String name, String description, String location) {
    }

    // --- Должность ---
    public record Post(long id, String code, long departmentId, String departmentName, String name,
                       String description) {
    }

    public record PostRequest(@NotNull Long departmentId, @NotBlank @Size(max = 255) String name,
                              String description) {
    }

    // --- Приоритет ---
    public record Priority(long id, String code, String name, String description, int sortOrder, String color,
                           Integer responseSlaMinutes, Integer resolutionSlaMinutes) {
    }

    public record PriorityRequest(
            @NotBlank @Size(max = 255) String name,
            String description,
            @Min(0) int sortOrder,
            @NotBlank @Pattern(regexp = "^#[0-9A-Fa-f]{6}$") String color,
            @Min(1) @Max(525_600) Integer responseSlaMinutes,
            @Min(1) @Max(525_600) Integer resolutionSlaMinutes
    ) {
    }

    // --- Статус ---
    public record Status(long id, String code, String name, String description, int sortOrder, Phase phase) {
    }

    public record StatusRequest(@NotBlank @Size(max = 255) String name, String description, @Min(0) int sortOrder,
                                @NotNull Phase phase) {
    }

    // --- Ведомство ---
    public record Institution(long id, String code, String name, String phoneNumber, String address) {
    }

    public record InstitutionRequest(
            @NotBlank @Size(max = 255) String name,
            @Pattern(regexp = "^\\+?[1-9]\\d{1,14}$", message = "номер в формате E.164, например +74951234567")
            String phoneNumber,
            @Size(max = 500) String address
    ) {
    }

    // --- Тип КЕ ---
    public record CiType(long id, String code, String name) {
    }

    public record CiTypeRequest(@NotBlank @Size(max = 255) String name) {
    }

    // --- ПО ---
    public record Software(long id, String code, String name, String version, String vendor, String licenseKey) {
    }

    public record SoftwareRequest(@NotBlank @Size(max = 255) String name, @Size(max = 100) String version,
                                  @Size(max = 255) String vendor, @Size(max = 255) String licenseKey) {
    }

    // --- Сотрудник ---
    public record Employee(long id, String code, String externalId, String lastName, String firstName,
                           String middleName, String email, long postId, String postName, long departmentId,
                           String departmentName, boolean active, String shortName) {
    }

    public record EmployeeRequest(
            @NotBlank @Size(max = 100) String lastName,
            @NotBlank @Size(max = 100) String firstName,
            @Size(max = 100) String middleName,
            @Size(max = 255) String email,
            @NotNull Long postId,
            @Size(max = 64) String externalId,
            boolean active
    ) {
    }

    /** Всё для выпадающих списков одним запросом. */
    public record Bundle(
            List<Department> departments,
            List<Post> posts,
            List<Priority> priorities,
            List<Status> statuses,
            List<Institution> institutions,
            List<CiType> ciTypes,
            List<Software> software,
            List<Employee> employees
    ) {
    }
}
