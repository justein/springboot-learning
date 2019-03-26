package com.nova.lyn.rules;
import static org.assertj.core.api.Assertions.assertThat;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.BoundListOperations;
import org.springframework.data.redis.core.BoundZSetOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.integration.channel.QueueChannel;
import org.springframework.messaging.Message;
import org.springframework.integration.test.util.TestUtils;

/**
 * @ClassName RedisAvailableTests
 * @Description TODO
 * @Author Lyn
 * @Date 2019/3/26 0026 下午 4:36
 * @Version 1.0
 */
public abstract class RedisAvailableTests {
    @Rule
    public RedisAvailableRule redisAvailableRule = new RedisAvailableRule();

    @BeforeClass
    public static void setupConnectionFactory() {
        RedisAvailableRule.setupConnectionFactory();
    }

    @AfterClass
    public static void cleanUpConnectionFactoryIfAny() {
        RedisAvailableRule.cleanUpConnectionFactoryIfAny();
    }

    protected RedisConnectionFactory getConnectionFactoryForTest() {
        return RedisAvailableRule.connectionFactory;
    }

    protected void awaitContainerSubscribed(RedisMessageListenerContainer container) throws Exception {
        awaitContainerSubscribedNoWait(container);
    }

    private void awaitContainerSubscribedNoWait(RedisMessageListenerContainer container) throws InterruptedException {
        RedisConnection connection = null;

        int n = 0;
        while (n++ < 300 &&
                (connection =
                        TestUtils.getPropertyValue(container, "subscriptionTask.connection", RedisConnection.class))
                        == null) {

            Thread.sleep(100);
        }
        assertThat(connection).as("RedisMessageListenerContainer Failed to Connect").isNotNull();

        n = 0;
        while (n++ < 300 && !connection.isSubscribed()) {
            Thread.sleep(100);
        }
        assertThat(n < 300).as("RedisMessageListenerContainer Failed to Subscribe").isTrue();
    }

    protected void awaitContainerSubscribedWithPatterns(RedisMessageListenerContainer container) throws Exception {
        this.awaitContainerSubscribed(container);
        RedisConnection connection = TestUtils.getPropertyValue(container, "subscriptionTask.connection",
                RedisConnection.class);

        int n = 0;
        while (n++ < 300 && connection.getSubscription().getPatterns().size() == 0) {
            Thread.sleep(100);
        }
        assertThat(n < 300).as("RedisMessageListenerContainer Failed to Subscribe with patterns").isTrue();
        // wait another second because of race condition
        Thread.sleep(1000);
    }

    protected void awaitFullySubscribed(RedisMessageListenerContainer container, RedisTemplate<?, ?> redisTemplate,
                                        String redisChannelName, QueueChannel channel, Object message) throws Exception {
        awaitContainerSubscribedNoWait(container);
        drain(channel);
        long now = System.currentTimeMillis();
        Message<?> received = null;
        while (received == null && System.currentTimeMillis() - now < 30000) {
            redisTemplate.convertAndSend(redisChannelName, message);
            received = channel.receive(1000);
        }
        drain(channel);
        assertThat(received).as("Container failed to fully start").isNotNull();
    }

    private void drain(QueueChannel channel) {
        while (channel.receive(0) != null) {
            // drain
        }
    }

    protected void prepareList(RedisConnectionFactory connectionFactory) {

        StringRedisTemplate redisTemplate = createStringRedisTemplate(connectionFactory);
        redisTemplate.delete("presidents");
        BoundListOperations<String, String> ops = redisTemplate.boundListOps("presidents");

        ops.rightPush("John Adams");

        ops.rightPush("Barack Obama");
        ops.rightPush("Thomas Jefferson");
        ops.rightPush("John Quincy Adams");
        ops.rightPush("Zachary Taylor");

        ops.rightPush("Theodore Roosevelt");
        ops.rightPush("Woodrow Wilson");
        ops.rightPush("George W. Bush");
        ops.rightPush("Franklin D. Roosevelt");
        ops.rightPush("Ronald Reagan");
        ops.rightPush("William J. Clinton");
        ops.rightPush("Abraham Lincoln");
        ops.rightPush("George Washington");
    }

    protected void prepareZset(RedisConnectionFactory connectionFactory) {

        StringRedisTemplate redisTemplate = createStringRedisTemplate(connectionFactory);

        redisTemplate.delete("presidents");
        BoundZSetOperations<String, String> ops = redisTemplate.boundZSetOps("presidents");

        ops.add("John Adams", 18);

        ops.add("Barack Obama", 21);
        ops.add("Thomas Jefferson", 19);
        ops.add("John Quincy Adams", 19);
        ops.add("Zachary Taylor", 19);

        ops.add("Theodore Roosevelt", 20);
        ops.add("Woodrow Wilson", 20);
        ops.add("George W. Bush", 21);
        ops.add("Franklin D. Roosevelt", 20);
        ops.add("Ronald Reagan", 20);
        ops.add("William J. Clinton", 20);
        ops.add("Abraham Lincoln", 19);
        ops.add("George Washington", 18);
    }

    protected void deletePresidents(RedisConnectionFactory connectionFactory) {
        this.deleteKey(connectionFactory, "presidents");
    }

    protected void deleteKey(RedisConnectionFactory connectionFactory, String key) {
        StringRedisTemplate redisTemplate = createStringRedisTemplate(connectionFactory);
        redisTemplate.delete(key);
    }

    protected StringRedisTemplate createStringRedisTemplate(RedisConnectionFactory connectionFactory) {
        StringRedisTemplate redisTemplate = new StringRedisTemplate();
        redisTemplate.setConnectionFactory(connectionFactory);
        redisTemplate.afterPropertiesSet();
        return redisTemplate;
    }

}
