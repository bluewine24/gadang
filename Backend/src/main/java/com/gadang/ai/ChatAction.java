package com.gadang.ai;

import java.util.Map;

/**
 * Chat response action rendered as a frontend navigation button.
 *
 * @param type button type, currently place or shared
 * @param label visible button text
 * @param route frontend route path
 * @param query route query parameters
 */
public record ChatAction(String type, String label, String route, Map<String, String> query) {
}
