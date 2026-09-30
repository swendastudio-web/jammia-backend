package com.jamia.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * JAMIA-wide settings the admin can change. There is always exactly one row (id = 1).
 */
@Entity
@Table(name = "app_settings")
public class AppSettings {

    // The only row. Its id is always 1.
    public static final short ID = 1;

    @Id
    private Short id;

    // How many days a new invite link works. Existing links keep the expiry they were created with.
    @Column(name = "invite_link_valid_days", nullable = false)
    private int inviteLinkValidDays;

    // JPA needs an empty constructor to create objects from database rows.
    protected AppSettings() {
    }

    public int getInviteLinkValidDays() {
        return inviteLinkValidDays;
    }

    public void setInviteLinkValidDays(int inviteLinkValidDays) {
        this.inviteLinkValidDays = inviteLinkValidDays;
    }
}
