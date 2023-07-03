package oram.utils;

import java.util.*;

public class TreePrinter {
	public static String print(List<String> values) {
		if (values == null || values.isEmpty()) {
			return "null";
		}

		int height = (int) (Math.log(values.size()) / Math.log(2)) + 1;
		int width = (int) Math.pow(2, height - 1) * 4;

		List<TreeNode> nodes = new ArrayList<>();
		for (String value : values) {
			nodes.add(value == null ? null : new TreeNode(value));
		}

		int level = 1;
		int index = 0;
		int levelSize = 1;
		int itemWidth = width / levelSize / 2;
		StringBuilder sbTotal = new StringBuilder();
		while (level <= height) {
			StringBuilder sb = new StringBuilder();
			for (int i = 0; i < levelSize; i++) {
				TreeNode node = index+i<nodes.size()?nodes.get(index + i):null;
				if (node != null) {
					printNode(sb, itemWidth, node.val);
				} else {
					printNode(sb, itemWidth, "{}");
				}
			}
			sbTotal.append(sb);

			index += levelSize;
			levelSize *= 2;
			itemWidth = width / levelSize / 2;
			level++;
		}
		return sbTotal.toString();
	}

	private static void printNode(StringBuilder sb, int width, String value) {
		int space = Math.max(0, width - value.length());
		for (int i = 0; i < space; i++) {
			sb.append(" ");
		}
		sb.append(value);
		for (int i = 0; i < space; i++) {
			sb.append(" ");
		}
		sb.append(" ");
	}

	private static class TreeNode {
		String val;
		TreeNode left;
		TreeNode right;

		TreeNode(String x) {
			val = x;
		}
	}
}
