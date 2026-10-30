package com.kaarislabs.chatbot.dev;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import com.kaarislabs.chatbot.chat.ChatContextFactory;
import com.kaarislabs.chatbot.chat.ChatJsonController;
import com.kaarislabs.chatbot.chat.ChatStreamService;
import com.kaarislabs.chatbot.service.CatalogueService;
import com.kaarislabs.chatbot.service.DocumentService;
import com.kaarislabs.chatbot.service.PricingService;
import com.kaarislabs.chatbot.service.ProductService;

/** The /dev endpoints must exist only under the "dev" profile. */
class DevProfileTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withBean(ProductService.class, () -> mock(ProductService.class))
            .withBean(CatalogueService.class, () -> mock(CatalogueService.class))
            .withBean(PricingService.class, () -> mock(PricingService.class))
            .withBean(DocumentService.class, () -> mock(DocumentService.class))
            .withUserConfiguration(DevController.class, DevExceptionHandler.class);

    @Test
    void devEndpointsAreAbsentWithoutDevProfile() {
        runner.run(ctx -> {
            assertThat(ctx).doesNotHaveBean(DevController.class);
            assertThat(ctx).doesNotHaveBean(DevExceptionHandler.class);
        });
        runner.withPropertyValues("spring.profiles.active=prod").run(ctx -> assertThat(ctx).doesNotHaveBean(DevController.class));
    }

    @Test
    void jsonChatExistsOnlyWithDevProfile() {
        ApplicationContextRunner chat = new ApplicationContextRunner()
                .withBean(ChatStreamService.class, () -> mock(ChatStreamService.class))
                .withBean(ChatContextFactory.class, () -> mock(ChatContextFactory.class))
                .withUserConfiguration(ChatJsonController.class);

        chat.run(ctx -> assertThat(ctx).doesNotHaveBean(ChatJsonController.class));
        chat.withPropertyValues("spring.profiles.active=prod")
                .run(ctx -> assertThat(ctx).doesNotHaveBean(ChatJsonController.class));
        chat.withPropertyValues("spring.profiles.active=dev")
                .run(ctx -> assertThat(ctx).hasSingleBean(ChatJsonController.class));
    }

    @Test
    void devEndpointsExistWithDevProfile() {
        runner.withPropertyValues("spring.profiles.active=dev").run(ctx -> assertThat(ctx).hasSingleBean(DevController.class));
    }
}
