package com.github.knokko.boiler.samples;

import com.github.knokko.boiler.buffers.MappedVkbBuffer;
import com.github.knokko.boiler.builders.BoilerBuilder;
import com.github.knokko.boiler.commands.SingleTimeCommands;
import org.lwjgl.util.vma.VmaAllocationCreateInfo;
import org.lwjgl.util.vma.VmaAllocationInfo;
import org.lwjgl.vulkan.*;

import static com.github.knokko.boiler.exceptions.VulkanFailureException.assertVkSuccess;
import static com.github.knokko.boiler.exceptions.VulkanFailureException.assertVmaSuccess;
import static com.github.knokko.boiler.utilities.ReflectionHelper.getIntConstantName;
import static java.lang.Math.max;
import static java.lang.Math.min;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.memPutInt;
import static org.lwjgl.system.MemoryUtil.memUTF8;
import static org.lwjgl.util.vma.Vma.*;
import static org.lwjgl.vulkan.KHRVideoEncodeH264.VK_KHR_VIDEO_ENCODE_H264_EXTENSION_NAME;
import static org.lwjgl.vulkan.KHRVideoEncodeH264.VK_VIDEO_CODEC_OPERATION_ENCODE_H264_BIT_KHR;
import static org.lwjgl.vulkan.KHRVideoEncodeQueue.*;
import static org.lwjgl.vulkan.KHRVideoQueue.*;
import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.vulkan.VK11.VK_FORMAT_G8_B8R8_2PLANE_420_UNORM;
import static org.lwjgl.vulkan.VK11.VK_FORMAT_G8_B8_R8_3PLANE_420_UNORM;
import static org.lwjgl.vulkan.VK13.VK_API_VERSION_1_3;
import static org.lwjgl.vulkan.video.STDVulkanVideoCodecH264.*;

public class VideoCoding {

	public static void main(String[] args) {
		var boiler = new BoilerBuilder(
				VK_API_VERSION_1_3, "HelloTriangle", VK_MAKE_VERSION(0, 1, 0)
		)
				.validation().forbidValidationErrors()
				.apiDump()
				.requiredDeviceExtensions(
						VK_KHR_VIDEO_QUEUE_EXTENSION_NAME,
						VK_KHR_VIDEO_ENCODE_QUEUE_EXTENSION_NAME,
						VK_KHR_VIDEO_ENCODE_H264_EXTENSION_NAME
				)
				.build();

		var h264Profile = VkVideoEncodeH264ProfileInfoKHR.calloc();
		h264Profile.sType$Default();
		h264Profile.stdProfileIdc(STD_VIDEO_H264_PROFILE_IDC_MAIN);

		var videoProfile = VkVideoProfileInfoKHR.calloc();
		videoProfile.sType$Default();
		videoProfile.videoCodecOperation(VK_VIDEO_CODEC_OPERATION_ENCODE_H264_BIT_KHR);
		videoProfile.chromaSubsampling(VK_VIDEO_CHROMA_SUBSAMPLING_420_BIT_KHR);
		videoProfile.chromaBitDepth(VK_VIDEO_COMPONENT_BIT_DEPTH_8_BIT_KHR);
		videoProfile.lumaBitDepth(VK_VIDEO_COMPONENT_BIT_DEPTH_8_BIT_KHR);
		videoProfile.pNext(h264Profile.address());

		var videoProfileList = VkVideoProfileListInfoKHR.calloc();
		videoProfileList.sType$Default();
		videoProfileList.pProfiles(VkVideoProfileInfoKHR.create(videoProfile.address(), 1));

		int width = 100;
		int height = 100;
		int sourceImageFormat = VK_FORMAT_UNDEFINED;
		int dpbImageFormat = VK_FORMAT_UNDEFINED;
		long videoSession;
		try (var stack = stackPush()) {
			var h264Capabilities = VkVideoEncodeH264CapabilitiesKHR.calloc(stack);
			h264Capabilities.sType$Default();

			var encodeCapabilities = VkVideoEncodeCapabilitiesKHR.calloc(stack);
			encodeCapabilities.sType$Default();
			encodeCapabilities.pNext(h264Capabilities.address());

			var videoCapabilities = VkVideoCapabilitiesKHR.calloc(stack);
			videoCapabilities.sType$Default();
			videoCapabilities.pNext(encodeCapabilities.address());

			assertVkSuccess(vkGetPhysicalDeviceVideoCapabilitiesKHR(
					boiler.vkPhysicalDevice(), videoProfile, videoCapabilities
			), "GetPhysicalDeviceVideoCapabilitiesKHR", null);
			width = max(width, videoCapabilities.minCodedExtent().width());
			height = max(height, videoCapabilities.minCodedExtent().height());
			width = min(width, videoCapabilities.maxCodedExtent().width());
			height = min(height, videoCapabilities.maxCodedExtent().height());
			System.out.println("Size will be " + width + " x " + height);

			int rateControlMode = VK_VIDEO_ENCODE_RATE_CONTROL_MODE_DEFAULT_KHR;
			int qualityLevel = 0;
			var qualityInfo = VkPhysicalDeviceVideoEncodeQualityLevelInfoKHR.calloc(stack);
			qualityInfo.sType$Default();
			qualityInfo.pVideoProfile(videoProfile);
			qualityInfo.qualityLevel(qualityLevel);

			var h264QualityProperties = VkVideoEncodeH264QualityLevelPropertiesKHR.calloc(stack);
			h264QualityProperties.sType$Default();

			var qualityProperties = VkVideoEncodeQualityLevelPropertiesKHR.calloc(stack);
			qualityProperties.sType$Default();
			qualityProperties.pNext(h264QualityProperties.address());

			assertVkSuccess(vkGetPhysicalDeviceVideoEncodeQualityLevelPropertiesKHR(
					boiler.vkPhysicalDevice(), qualityInfo, qualityProperties
			), "GetPhysicalDeviceVideoEncodeQualityLevelPropertiesKHR", null);

			var formatInfo = VkPhysicalDeviceVideoFormatInfoKHR.calloc(stack);
			formatInfo.sType$Default();
			formatInfo.pNext(videoProfileList.address());
			formatInfo.imageUsage(VK_IMAGE_USAGE_VIDEO_ENCODE_SRC_BIT_KHR | VK_IMAGE_USAGE_STORAGE_BIT);

			var formatPropertyCount = stack.callocInt(1);
			assertVkSuccess(vkGetPhysicalDeviceVideoFormatPropertiesKHR(
					boiler.vkPhysicalDevice(), formatInfo, formatPropertyCount, null
			), "GetPhysicalDeviceVideoFormatPropertiesKHR", "source count");

			var formatProperties = VkVideoFormatPropertiesKHR.calloc(formatPropertyCount.get(0), stack);
			for (int index = 0; index < formatProperties.capacity(); index++) {
				//noinspection resource
				formatProperties.get(index).sType$Default();
			}
			assertVkSuccess(vkGetPhysicalDeviceVideoFormatPropertiesKHR(
					boiler.vkPhysicalDevice(), formatInfo, formatPropertyCount, formatProperties
			), "GetPhysicalDeviceVideoFormatPropertiesKHR", "source data");

			for (int index = 0; index < formatProperties.capacity(); index++) {
				int format = formatProperties.get(index).format();
				if (format == VK_FORMAT_G8_B8R8_2PLANE_420_UNORM || format == VK_FORMAT_G8_B8_R8_3PLANE_420_UNORM) {
					sourceImageFormat = format;
				}
				System.out.println("supports source format " + getIntConstantName(
						VK13.class, format, "VK_FORMAT", "", "unknown")
				);
			}

			if (sourceImageFormat == VK_FORMAT_UNDEFINED) {
				throw new UnsupportedOperationException("Failed to find a supported source video image format");
			}

			formatInfo.imageUsage(VK_IMAGE_USAGE_VIDEO_ENCODE_DPB_BIT_KHR);

			assertVkSuccess(vkGetPhysicalDeviceVideoFormatPropertiesKHR(
					boiler.vkPhysicalDevice(), formatInfo, formatPropertyCount, null
			), "GetPhysicalDeviceVideoFormatPropertiesKHR", "dpb count");

			formatProperties = VkVideoFormatPropertiesKHR.calloc(formatPropertyCount.get(0), stack);
			for (int index = 0; index < formatProperties.capacity(); index++) {
				//noinspection resource
				formatProperties.get(index).sType$Default();
			}
			assertVkSuccess(vkGetPhysicalDeviceVideoFormatPropertiesKHR(
					boiler.vkPhysicalDevice(), formatInfo, formatPropertyCount, formatProperties
			), "GetPhysicalDeviceVideoFormatPropertiesKHR", "dpb data");

			for (int index = 0; index < formatProperties.capacity(); index++) {
				int format = formatProperties.get(index).format();
				if (format == VK_FORMAT_G8_B8R8_2PLANE_420_UNORM || format == VK_FORMAT_G8_B8_R8_3PLANE_420_UNORM) {
					dpbImageFormat = format;
				}
				System.out.println("supports dpb format " + getIntConstantName(
						VK13.class, format, "VK_FORMAT", "", "unknown")
				);
			}

			if (dpbImageFormat == VK_FORMAT_UNDEFINED) {
				throw new UnsupportedOperationException("Failed to find a supported dpb video image format");
			}

			var h264HeaderVersion = VkExtensionProperties.calloc(stack);
			memUTF8(
					VK_STD_VULKAN_VIDEO_CODEC_H264_ENCODE_EXTENSION_NAME,
					true, h264HeaderVersion.extensionName()
			);
			memPutInt(
					h264HeaderVersion.address() + VkExtensionProperties.SPECVERSION,
					VK_STD_VULKAN_VIDEO_CODEC_H264_ENCODE_SPEC_VERSION
			);

			var ciSession = VkVideoSessionCreateInfoKHR.calloc(stack);
			ciSession.sType$Default();
			ciSession.pVideoProfile(videoProfile);
			ciSession.queueFamilyIndex(boiler.queueFamilies().videoEncode().index());
			ciSession.pictureFormat(sourceImageFormat);
			ciSession.maxCodedExtent().set(width, height);
			ciSession.maxDpbSlots(16);
			ciSession.maxActiveReferencePictures(16);
			ciSession.referencePictureFormat(dpbImageFormat);
			ciSession.pStdHeaderVersion(h264HeaderVersion);

			var pSession = stack.callocLong(1);
			assertVkSuccess(vkCreateVideoSessionKHR(
					boiler.vkDevice(), ciSession, null, pSession
			), "CreateVideoSessionKHR", null);
			videoSession = pSession.get(0);
		}

		long[] vmaAllocations;
		try (var stack = stackPush()) {
			var numRequirements = stack.callocInt(1);
			assertVkSuccess(vkGetVideoSessionMemoryRequirementsKHR(
					boiler.vkDevice(), videoSession, numRequirements, null
			), "GetVideoSessionMemoryRequirementsKHR", "count");

			vmaAllocations = new long[numRequirements.get(0)];
			if (numRequirements.get(0) > 0) {

				var requirements = VkVideoSessionMemoryRequirementsKHR.calloc(numRequirements.get(0), stack);
				for (int index = 0; index < requirements.capacity(); index++) {
					//noinspection resource
					requirements.get(index).sType$Default();
				}
				assertVkSuccess(vkGetVideoSessionMemoryRequirementsKHR(
						boiler.vkDevice(), videoSession, numRequirements, requirements
				), "GetVideoSessionMemoryRequirementsKHR", "data");

				var bindMemory = VkBindVideoSessionMemoryInfoKHR.calloc(numRequirements.get(0), stack);
				var ciAllocation = VmaAllocationCreateInfo.calloc(stack);
				var allocationInfo = VmaAllocationInfo.calloc(stack);
				var pAllocation = stack.callocPointer(1);
				for (int index = 0; index < bindMemory.capacity(); index++) {
					var requirement = requirements.get(index);
					var bind = bindMemory.get(index);

					ciAllocation.memoryTypeBits(requirement.memoryRequirements().memoryTypeBits());
					assertVmaSuccess(vmaAllocateMemory(
							boiler.vmaAllocator(), requirement.memoryRequirements(),
							ciAllocation, pAllocation, allocationInfo
					), "AllocateMemory", "video");
					vmaAllocations[index] = pAllocation.get(0);

					bind.sType$Default();
					bind.memory(allocationInfo.deviceMemory());
					bind.memoryBindIndex(requirement.memoryBindIndex());
					bind.memoryOffset(allocationInfo.offset());
					bind.memorySize(allocationInfo.size());
				}

				assertVkSuccess(vkBindVideoSessionMemoryKHR(
						boiler.vkDevice(), videoSession, bindMemory
				), "BindVideoSessionMemoryKHR", null);
			}
		}

		long bitStreamBuffer, bitStreamAllocation;
		MappedVkbBuffer bitStreamData;
		try (var stack = stackPush()) {
			var ciBuffer = VkBufferCreateInfo.calloc(stack);
			ciBuffer.sType$Default();
			ciBuffer.size(4L * width * height);
			ciBuffer.pNext(videoProfileList.address());
			ciBuffer.usage(VK_BUFFER_USAGE_VIDEO_ENCODE_DST_BIT_KHR);
			ciBuffer.sharingMode(VK_SHARING_MODE_EXCLUSIVE);

			var ciAllocation = VmaAllocationCreateInfo.calloc(stack);
			ciAllocation.usage(VMA_MEMORY_USAGE_GPU_TO_CPU);

			var pBuffer = stack.callocLong(1);
			var pAllocation = stack.callocPointer(1);
			assertVmaSuccess(vmaCreateBuffer(
					boiler.vmaAllocator(), ciBuffer, ciAllocation, pBuffer, pAllocation, null
			), "CreateBuffer", "bit stream");
			bitStreamBuffer = pBuffer.get(0);
			bitStreamAllocation = pAllocation.get(0);

			var pHostAddress = stack.callocPointer(1);
			assertVmaSuccess(vmaMapMemory(
					boiler.vmaAllocator(), bitStreamAllocation, pHostAddress
			), "MapMemory", "bit stream");
			bitStreamData = new MappedVkbBuffer(bitStreamBuffer, 0, ciBuffer.size(), pHostAddress.get(0));
		}

		int numReferenceImages = 2;
		long[] dpbImages = new long[numReferenceImages];
		long[] dpbAllocations = new long[numReferenceImages];
		long[] dpbImageViews = new long[numReferenceImages];
		for (int index = 0; index < numReferenceImages; index++) {
			try (var stack = stackPush()) {
				var ciImage = VkImageCreateInfo.calloc(stack);
				ciImage.sType$Default();
				ciImage.pNext(videoProfileList.address());
				ciImage.imageType(VK_IMAGE_TYPE_2D);
				ciImage.format(dpbImageFormat);
				ciImage.extent().set(width, height, 1);
				ciImage.mipLevels(1);
				ciImage.arrayLayers(1);
				ciImage.samples(VK_SAMPLE_COUNT_1_BIT);
				ciImage.tiling(VK_IMAGE_TILING_OPTIMAL);
				ciImage.usage(VK_IMAGE_USAGE_VIDEO_ENCODE_DPB_BIT_KHR);
				ciImage.sharingMode(VK_SHARING_MODE_EXCLUSIVE);
				ciImage.initialLayout(VK_IMAGE_LAYOUT_UNDEFINED);

				var ciAllocation = VmaAllocationCreateInfo.calloc(stack);
				ciAllocation.usage(VMA_MEMORY_USAGE_GPU_ONLY);

				var pImage = stack.callocLong(1);
				var pAllocation = stack.callocPointer(1);
				assertVmaSuccess(vmaCreateImage(
						boiler.vmaAllocator(), ciImage, ciAllocation, pImage, pAllocation, null
				), "CreateImage", "reference (dpb)");
				dpbImages[index] = pImage.get(0);
				dpbAllocations[index] = pAllocation.get(0);

				var ciView = VkImageViewCreateInfo.calloc(stack);
				ciView.sType$Default();
				ciView.image(dpbImages[index]);
				ciView.viewType(VK_IMAGE_VIEW_TYPE_2D);
				ciView.format(dpbImageFormat);
				boiler.images.subresourceRange(stack, ciView.subresourceRange(), VK_IMAGE_ASPECT_COLOR_BIT);

				var pView = stack.callocLong(1);
				assertVkSuccess(vkCreateImageView(
						boiler.vkDevice(), ciView, null, pView
				), "CreateImageView", "dpb image");
				dpbImageViews[index] = pView.get(0);
			}
		}

		long sourceImage, sourceImageEncodeView, sourceImageTransferView, sourceAllocation;
		try (var stack = stackPush()) {
			var ciImage = VkImageCreateInfo.calloc(stack);
			ciImage.sType$Default();
			ciImage.pNext(videoProfileList.address());
			ciImage.imageType(VK_IMAGE_TYPE_2D);
			ciImage.format(sourceImageFormat);
			ciImage.extent().set(width, height, 1);
			ciImage.mipLevels(1);
			ciImage.arrayLayers(1);
			ciImage.samples(VK_SAMPLE_COUNT_1_BIT);
			ciImage.tiling(VK_IMAGE_TILING_OPTIMAL);
			ciImage.usage(VK_IMAGE_USAGE_VIDEO_ENCODE_SRC_BIT_KHR | VK_IMAGE_USAGE_STORAGE_BIT);
			if (boiler.queueFamilies().videoEncode() == boiler.queueFamilies().compute()) {
				ciImage.sharingMode(VK_SHARING_MODE_EXCLUSIVE);
			} else {
				ciImage.sharingMode(VK_SHARING_MODE_CONCURRENT);
				ciImage.queueFamilyIndexCount(2);
				ciImage.pQueueFamilyIndices(stack.ints(
						boiler.queueFamilies().videoEncode().index(),
						boiler.queueFamilies().compute().index()
				));
			}
			ciImage.initialLayout(VK_IMAGE_LAYOUT_UNDEFINED);
			// TODO flags?

			var ciAllocation = VmaAllocationCreateInfo.calloc(stack);
			ciAllocation.usage(VMA_MEMORY_USAGE_AUTO_PREFER_DEVICE);

			var pImage = stack.callocLong(1);
			var pAllocation = stack.callocPointer(1);
			assertVmaSuccess(vmaCreateImage(
					boiler.vmaAllocator(), ciImage, ciAllocation, pImage, pAllocation, null
			), "CreateImage", "source");
			sourceImage = pImage.get(0);
			sourceAllocation = pAllocation.get(0);

			var viewUsage = VkImageViewUsageCreateInfo.calloc(stack);
			viewUsage.sType$Default();
			viewUsage.usage(VK_IMAGE_USAGE_VIDEO_ENCODE_SRC_BIT_KHR);

			var ciView = VkImageViewCreateInfo.calloc(stack);
			ciView.sType$Default();
			ciView.pNext(viewUsage.address());
			ciView.image(sourceImage);
			ciView.viewType(VK_IMAGE_VIEW_TYPE_2D);
			ciView.format(sourceImageFormat);
			boiler.images.subresourceRange(stack, ciView.subresourceRange(), VK_IMAGE_ASPECT_COLOR_BIT);

			var pView = stack.callocLong(1);
			assertVkSuccess(vkCreateImageView(
					boiler.vkDevice(), ciView, null, pView
			), "CreateImageView", "source image");
			sourceImageEncodeView = pView.get(0);

			viewUsage.usage(VK_IMAGE_USAGE_STORAGE_BIT);
			int numPlanes = sourceImageFormat == VK_FORMAT_G8_B8R8_2PLANE_420_UNORM ? 2 : 3;
		}
//		long sessionParameters = VK_NULL_HANDLE;
//		var commands = new SingleTimeCommands(boiler, boiler.queueFamilies().videoEncode());
//		commands.submit("VideoEncoding", recorder -> {
//			var biCoding = VkVideoBeginCodingInfoKHR.calloc(recorder.stack);
//			biCoding.sType$Default();
//			biCoding.videoSession(videoSession);
//			biCoding.videoSessionParameters(sessionParameters);
//
//			vkCmdBeginVideoCodingKHR(recorder.commandBuffer, biCoding);
//		});
//		commands.destroy();

		vkDestroyImageView(boiler.vkDevice(), sourceImageEncodeView, null);
		//vkDestroyImageView(boiler.vkDevice(), sourceImageTransferView, null);
		vmaDestroyImage(boiler.vmaAllocator(), sourceImage, sourceAllocation);
		for (long imageView : dpbImageViews) vkDestroyImageView(boiler.vkDevice(), imageView, null);
		for (int index = 0; index < numReferenceImages; index++) {
			vmaDestroyImage(boiler.vmaAllocator(), dpbImages[index], dpbAllocations[index]);
		}
		vmaDestroyBuffer(boiler.vmaAllocator(), bitStreamBuffer, bitStreamAllocation);
		vkDestroyVideoSessionKHR(boiler.vkDevice(), videoSession, null);
		for (long vmaAllocation : vmaAllocations) vmaFreeMemory(boiler.vmaAllocator(), vmaAllocation);
		h264Profile.close();
		videoProfileList.close();
		videoProfile.close();
		boiler.destroyInitialObjects();
	}
}
