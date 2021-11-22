package client.render.vk.draw.cmd;

import client.graphics2.vk.device.Device;
import client.render.vk.Global;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkCommandPoolCreateInfo;

import java.nio.LongBuffer;

public class CommandPool {
    private final long handle;

    public CommandPool(MemoryStack stack, Device device) {
        VkCommandPoolCreateInfo createInfo = VkCommandPoolCreateInfo.malloc(stack)
                .sType$Default()
                .flags(VK10.VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT)
                .pNext(0)
                .queueFamilyIndex(device.getGraphicsQueueFamilyIndex());

        LongBuffer pCommandPool = stack.mallocLong(1);
        Global.vkCheck(VK10.vkCreateCommandPool(device.getHandle(), createInfo, null, pCommandPool), "Failed to create command pool");
        handle = pCommandPool.get(0);
    }

    public void destroy(Device device) {
        VK10.vkDestroyCommandPool(device.getHandle(), handle, null);
    }

    public long getHandle() {
        return handle;
    }
}
