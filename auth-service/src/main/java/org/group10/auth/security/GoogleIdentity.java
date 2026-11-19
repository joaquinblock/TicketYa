package org.group10.auth.security;

/**
 * Quién es la persona según el ID token de Google, ya verificado.
 *
 * @param subject el {@code sub} de Google: estable y único, se guarda en {@code user.idp_subject}
 * @param email   verificado por Google y en minúsculas
 */
public record
GoogleIdentity(String subject, String email, String firstName, String lastName) {
}
