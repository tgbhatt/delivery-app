package com.bt.deliveryapp.seeder;

import com.bt.deliveryapp.enums.UserRole;
import com.bt.deliveryapp.model.Agent;
import com.bt.deliveryapp.model.User;
import com.bt.deliveryapp.repository.AgentRepository;
import com.bt.deliveryapp.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Order(2)
public class AgentSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final AgentRepository agentRepository;

    public AgentSeeder(UserRepository userRepository, AgentRepository agentRepository) {
        this.userRepository = userRepository;
        this.agentRepository = agentRepository;
    }

    @Override
    public void run(String... args) {
        List<User> agentUsers = userRepository.findByRole(UserRole.AGENT);

        for (User user : agentUsers) {
            boolean profileExists = agentRepository.findByUser(user).isPresent();
            if (!profileExists) {
                agentRepository.save(new Agent(user));
            }
        }
    }
}
