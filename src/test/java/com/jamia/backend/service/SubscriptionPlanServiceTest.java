package com.jamia.backend.service;

import com.jamia.backend.entity.SubscriptionPlan;
import com.jamia.backend.entity.SubscriptionPlanCode;
import com.jamia.backend.exception.ResourceNotFoundException;
import com.jamia.backend.repository.SubscriptionPlanRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Tests changing a plan's room-size limit. The repository is a mock.
 */
@ExtendWith(MockitoExtension.class)
class SubscriptionPlanServiceTest {

    @Mock
    private SubscriptionPlanRepository subscriptionPlanRepository;

    private SubscriptionPlanService subscriptionPlanService;

    @BeforeEach
    void setUp() {
        subscriptionPlanService = new SubscriptionPlanService(subscriptionPlanRepository);
    }

    @Test
    void updateMaxMembersPerRoom_changesTheLimit() {
        SubscriptionPlan free = new SubscriptionPlan(SubscriptionPlanCode.FREE, 5);
        when(subscriptionPlanRepository.findByCode(SubscriptionPlanCode.FREE)).thenReturn(Optional.of(free));

        SubscriptionPlan updated = subscriptionPlanService.updateMaxMembersPerRoom(SubscriptionPlanCode.FREE, 8);

        assertThat(updated.getMaxMembersPerRoom()).isEqualTo(8);
    }

    @Test
    void updateMaxMembersPerRoom_failsClearlyIfThePlanIsMissing() {
        when(subscriptionPlanRepository.findByCode(SubscriptionPlanCode.GOLD)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> subscriptionPlanService.updateMaxMembersPerRoom(SubscriptionPlanCode.GOLD, 50))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
