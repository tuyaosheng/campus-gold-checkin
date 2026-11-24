package com.school.checkin.utils;

import com.school.checkin.common.ErrorCode;
import com.school.checkin.dto.RankSettingSaveRequest;
import com.school.checkin.exception.BusinessException;
import com.school.checkin.vo.RankSettingVO;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;

public final class RankRuleUtil {

    public static final String MODE_COUNT = "COUNT";
    public static final String MODE_PERCENT = "PERCENT";

    private static final List<String> TITLES_HIGH_TO_LOW = Arrays.asList(
            "荣耀王者", "无双王者", "最强王者", "至尊星耀", "永恒钻石", "尊贵铂金", "荣耀黄金", "秩序白银", "倔强青铜");
    private static final List<String> CSS_HIGH_TO_LOW = Arrays.asList(
            "glory", "legend", "king", "starry", "diamond", "platinum", "gold", "silver", "bronze");

    private RankRuleUtil() {
    }

    public static RankSettingVO defaultSettings() {
        RankSettingVO vo = new RankSettingVO();
        vo.setMode(MODE_COUNT);
        vo.setBronzeCount(0);
        vo.setBronze2Count(5);
        vo.setBronze1Count(10);
        vo.setSilverCount(15);
        vo.setSilver2Count(20);
        vo.setSilver1Count(25);
        vo.setGoldCount(32);
        vo.setGold3Count(39);
        vo.setGold2Count(46);
        vo.setGold1Count(53);
        vo.setPlatinumCount(61);
        vo.setPlatinum3Count(69);
        vo.setPlatinum2Count(77);
        vo.setPlatinum1Count(85);
        vo.setDiamondCount(94);
        vo.setDiamond4Count(103);
        vo.setDiamond3Count(112);
        vo.setDiamond2Count(121);
        vo.setDiamond1Count(130);
        vo.setStarryCount(136);
        vo.setStarry4Count(141);
        vo.setStarry3Count(146);
        vo.setStarry2Count(150);
        vo.setStarry1Count(152);
        vo.setKingCount(153);
        vo.setLegendCount(159);
        vo.setGloryCount(165);
        vo.setGloryPercent(5);
        vo.setLegendPercent(5);
        vo.setKingPercent(10);
        vo.setStarryPercent(10);
        vo.setDiamondPercent(15);
        vo.setPlatinumPercent(15);
        vo.setGoldPercent(15);
        vo.setSilverPercent(15);
        vo.setBronzePercent(10);
        return vo;
    }

    public static void validate(RankSettingSaveRequest request) {
        String mode = normalizeMode(request.getMode());
        if (MODE_COUNT.equals(mode)) {
            int[] values = {
                    req(request.getBronzeCount()), req(request.getBronze2Count()), req(request.getBronze1Count()),
                    req(request.getSilverCount()), req(request.getSilver2Count()), req(request.getSilver1Count()),
                    req(request.getGoldCount()), req(request.getGold3Count()), req(request.getGold2Count()), req(request.getGold1Count()),
                    req(request.getPlatinumCount()), req(request.getPlatinum3Count()), req(request.getPlatinum2Count()), req(request.getPlatinum1Count()),
                    req(request.getDiamondCount()), req(request.getDiamond4Count()), req(request.getDiamond3Count()), req(request.getDiamond2Count()), req(request.getDiamond1Count()),
                    req(request.getStarryCount()), req(request.getStarry4Count()), req(request.getStarry3Count()), req(request.getStarry2Count()), req(request.getStarry1Count()),
                    req(request.getKingCount()), req(request.getLegendCount()), req(request.getGloryCount())
            };
            for (int i = 0; i < values.length; i++) {
                if (values[i] < 0) {
                    throw new BusinessException(ErrorCode.BAD_REQUEST, "次数阈值不能小于 0");
                }
                if (i > 0 && values[i] < values[i - 1]) {
                    throw new BusinessException(ErrorCode.BAD_REQUEST, "次数阈值必须从低到高递增");
                }
            }
            return;
        }
        int[] values = {
                req(request.getGloryPercent()), req(request.getLegendPercent()), req(request.getKingPercent()), req(request.getStarryPercent()),
                req(request.getDiamondPercent()), req(request.getPlatinumPercent()), req(request.getGoldPercent()), req(request.getSilverPercent()), req(request.getBronzePercent())
        };
        int sum = 0;
        for (int value : values) {
            if (value < 0) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "百分比不能小于 0");
            }
            sum += value;
        }
        if (sum != 100) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "百分比分配总和必须等于 100");
        }
    }

    public static String normalizeMode(String mode) {
        return MODE_PERCENT.equalsIgnoreCase(mode) ? MODE_PERCENT : MODE_COUNT;
    }

    public static RankResult resolveByCount(int totalCount, RankSettingVO setting) {
        RankSettingVO current = setting == null ? defaultSettings() : setting;
        for (CountTier tier : buildCountTiers(current)) {
            if (totalCount >= tier.threshold) {
                return new RankResult(tier.title, tier.cssCode);
            }
        }
        return new RankResult("倔强青铜Ⅲ", "bronze");
    }

    public static RankResult resolveByPercent(int index, int totalSize, RankSettingVO setting) {
        RankSettingVO current = setting == null ? defaultSettings() : setting;
        if (totalSize <= 0) {
            return new RankResult("倔强青铜", "bronze");
        }
        double percentile = (index + 1) * 100.0 / totalSize;
        int cumulative = 0;
        int[] percents = {
                safe(current.getGloryPercent()), safe(current.getLegendPercent()), safe(current.getKingPercent()),
                safe(current.getStarryPercent()), safe(current.getDiamondPercent()), safe(current.getPlatinumPercent()),
                safe(current.getGoldPercent()), safe(current.getSilverPercent()), safe(current.getBronzePercent())
        };
        for (int i = 0; i < percents.length; i++) {
            cumulative += percents[i];
            if (percentile <= cumulative || i == percents.length - 1) {
                return new RankResult(TITLES_HIGH_TO_LOW.get(i), CSS_HIGH_TO_LOW.get(i));
            }
        }
        return new RankResult("倔强青铜", "bronze");
    }

    public static String serialize(RankSettingVO setting) {
        RankSettingVO current = setting == null ? defaultSettings() : setting;
        return String.join("\n",
                "mode=" + normalizeMode(current.getMode()),
                "bronzeCount=" + safe(current.getBronzeCount()),
                "bronze2Count=" + safe(current.getBronze2Count()),
                "bronze1Count=" + safe(current.getBronze1Count()),
                "silverCount=" + safe(current.getSilverCount()),
                "silver2Count=" + safe(current.getSilver2Count()),
                "silver1Count=" + safe(current.getSilver1Count()),
                "goldCount=" + safe(current.getGoldCount()),
                "gold3Count=" + safe(current.getGold3Count()),
                "gold2Count=" + safe(current.getGold2Count()),
                "gold1Count=" + safe(current.getGold1Count()),
                "platinumCount=" + safe(current.getPlatinumCount()),
                "platinum3Count=" + safe(current.getPlatinum3Count()),
                "platinum2Count=" + safe(current.getPlatinum2Count()),
                "platinum1Count=" + safe(current.getPlatinum1Count()),
                "diamondCount=" + safe(current.getDiamondCount()),
                "diamond4Count=" + safe(current.getDiamond4Count()),
                "diamond3Count=" + safe(current.getDiamond3Count()),
                "diamond2Count=" + safe(current.getDiamond2Count()),
                "diamond1Count=" + safe(current.getDiamond1Count()),
                "starryCount=" + safe(current.getStarryCount()),
                "starry4Count=" + safe(current.getStarry4Count()),
                "starry3Count=" + safe(current.getStarry3Count()),
                "starry2Count=" + safe(current.getStarry2Count()),
                "starry1Count=" + safe(current.getStarry1Count()),
                "kingCount=" + safe(current.getKingCount()),
                "legendCount=" + safe(current.getLegendCount()),
                "gloryCount=" + safe(current.getGloryCount()),
                "gloryPercent=" + safe(current.getGloryPercent()),
                "legendPercent=" + safe(current.getLegendPercent()),
                "kingPercent=" + safe(current.getKingPercent()),
                "starryPercent=" + safe(current.getStarryPercent()),
                "diamondPercent=" + safe(current.getDiamondPercent()),
                "platinumPercent=" + safe(current.getPlatinumPercent()),
                "goldPercent=" + safe(current.getGoldPercent()),
                "silverPercent=" + safe(current.getSilverPercent()),
                "bronzePercent=" + safe(current.getBronzePercent()));
    }

    public static RankSettingVO deserialize(String content) {
        RankSettingVO vo = defaultSettings();
        if (content == null || content.trim().isEmpty()) {
            return vo;
        }
        String[] lines = content.split("\\r?\\n");
        for (String line : lines) {
            String[] pair = line.split("=", 2);
            if (pair.length != 2) {
                continue;
            }
            String key = pair[0].trim();
            String value = pair[1].trim();
            apply(vo, key, value);
        }
        vo.setMode(normalizeMode(vo.getMode()));
        return vo;
    }

    public static String buildRuleTip(RankSettingVO setting) {
        RankSettingVO current = setting == null ? defaultSettings() : setting;
        if (MODE_PERCENT.equals(normalizeMode(current.getMode()))) {
            return String.join(" | ",
                    "荣耀王者前" + safe(current.getGloryPercent()) + "%",
                    "无双王者前" + safe(current.getLegendPercent()) + "%",
                    "最强王者前" + safe(current.getKingPercent()) + "%",
                    "至尊星耀前" + safe(current.getStarryPercent()) + "%",
                    "永恒钻石前" + safe(current.getDiamondPercent()) + "%",
                    "尊贵铂金前" + safe(current.getPlatinumPercent()) + "%",
                    "荣耀黄金前" + safe(current.getGoldPercent()) + "%",
                    "秩序白银前" + safe(current.getSilverPercent()) + "%",
                    "倔强青铜前" + safe(current.getBronzePercent()) + "%");
        }
        int kingCount = safe(current.getKingCount());
        int legendCount = safe(current.getLegendCount());
        int gloryCount = safe(current.getGloryCount());
        return String.join(" | ",
                "青铜Ⅲ/Ⅱ/Ⅰ：" + safe(current.getBronzeCount()) + "/" + safe(current.getBronze2Count()) + "/" + safe(current.getBronze1Count()) + "次",
                "白银Ⅲ/Ⅱ/Ⅰ：" + safe(current.getSilverCount()) + "/" + safe(current.getSilver2Count()) + "/" + safe(current.getSilver1Count()) + "次",
                "黄金Ⅳ/Ⅲ/Ⅱ/Ⅰ：" + safe(current.getGoldCount()) + "/" + safe(current.getGold3Count()) + "/" + safe(current.getGold2Count()) + "/" + safe(current.getGold1Count()) + "次",
                "铂金Ⅳ/Ⅲ/Ⅱ/Ⅰ：" + safe(current.getPlatinumCount()) + "/" + safe(current.getPlatinum3Count()) + "/" + safe(current.getPlatinum2Count()) + "/" + safe(current.getPlatinum1Count()) + "次",
                "钻石Ⅴ/Ⅳ/Ⅲ/Ⅱ/Ⅰ：" + safe(current.getDiamondCount()) + "/" + safe(current.getDiamond4Count()) + "/" + safe(current.getDiamond3Count()) + "/" + safe(current.getDiamond2Count()) + "/" + safe(current.getDiamond1Count()) + "次",
                "星耀Ⅴ/Ⅳ/Ⅲ/Ⅱ/Ⅰ：" + safe(current.getStarryCount()) + "/" + safe(current.getStarry4Count()) + "/" + safe(current.getStarry3Count()) + "/" + safe(current.getStarry2Count()) + "/" + safe(current.getStarry1Count()) + "次",
                "最强王者：" + kingCount + "次起",
                "无双王者：" + legendCount + "次起",
                "荣耀王者：" + gloryCount + "次起，以上每多1次+1星");
    }

    private static void apply(RankSettingVO vo, String key, String value) {
        switch (key) {
            case "mode": vo.setMode(normalizeMode(value)); break;
            case "bronzeCount": vo.setBronzeCount(parse(value, vo.getBronzeCount())); break;
            case "bronze2Count": vo.setBronze2Count(parse(value, vo.getBronze2Count())); break;
            case "bronze1Count": vo.setBronze1Count(parse(value, vo.getBronze1Count())); break;
            case "silverCount": vo.setSilverCount(parse(value, vo.getSilverCount())); break;
            case "silver2Count": vo.setSilver2Count(parse(value, vo.getSilver2Count())); break;
            case "silver1Count": vo.setSilver1Count(parse(value, vo.getSilver1Count())); break;
            case "goldCount": vo.setGoldCount(parse(value, vo.getGoldCount())); break;
            case "gold3Count": vo.setGold3Count(parse(value, vo.getGold3Count())); break;
            case "gold2Count": vo.setGold2Count(parse(value, vo.getGold2Count())); break;
            case "gold1Count": vo.setGold1Count(parse(value, vo.getGold1Count())); break;
            case "platinumCount": vo.setPlatinumCount(parse(value, vo.getPlatinumCount())); break;
            case "platinum3Count": vo.setPlatinum3Count(parse(value, vo.getPlatinum3Count())); break;
            case "platinum2Count": vo.setPlatinum2Count(parse(value, vo.getPlatinum2Count())); break;
            case "platinum1Count": vo.setPlatinum1Count(parse(value, vo.getPlatinum1Count())); break;
            case "diamondCount": vo.setDiamondCount(parse(value, vo.getDiamondCount())); break;
            case "diamond4Count": vo.setDiamond4Count(parse(value, vo.getDiamond4Count())); break;
            case "diamond3Count": vo.setDiamond3Count(parse(value, vo.getDiamond3Count())); break;
            case "diamond2Count": vo.setDiamond2Count(parse(value, vo.getDiamond2Count())); break;
            case "diamond1Count": vo.setDiamond1Count(parse(value, vo.getDiamond1Count())); break;
            case "starryCount": vo.setStarryCount(parse(value, vo.getStarryCount())); break;
            case "starry4Count": vo.setStarry4Count(parse(value, vo.getStarry4Count())); break;
            case "starry3Count": vo.setStarry3Count(parse(value, vo.getStarry3Count())); break;
            case "starry2Count": vo.setStarry2Count(parse(value, vo.getStarry2Count())); break;
            case "starry1Count": vo.setStarry1Count(parse(value, vo.getStarry1Count())); break;
            case "kingCount": vo.setKingCount(parse(value, vo.getKingCount())); break;
            case "legendCount": vo.setLegendCount(parse(value, vo.getLegendCount())); break;
            case "gloryCount": vo.setGloryCount(parse(value, vo.getGloryCount())); break;
            case "gloryPercent": vo.setGloryPercent(parse(value, vo.getGloryPercent())); break;
            case "legendPercent": vo.setLegendPercent(parse(value, vo.getLegendPercent())); break;
            case "kingPercent": vo.setKingPercent(parse(value, vo.getKingPercent())); break;
            case "starryPercent": vo.setStarryPercent(parse(value, vo.getStarryPercent())); break;
            case "diamondPercent": vo.setDiamondPercent(parse(value, vo.getDiamondPercent())); break;
            case "platinumPercent": vo.setPlatinumPercent(parse(value, vo.getPlatinumPercent())); break;
            case "goldPercent": vo.setGoldPercent(parse(value, vo.getGoldPercent())); break;
            case "silverPercent": vo.setSilverPercent(parse(value, vo.getSilverPercent())); break;
            case "bronzePercent": vo.setBronzePercent(parse(value, vo.getBronzePercent())); break;
            default: break;
        }
    }

    private static int parse(String value, Integer fallback) {
        try {
            return Integer.parseInt(value);
        } catch (Exception ex) {
            return safe(fallback);
        }
    }

    private static int safe(Integer value) {
        return value == null ? 0 : value;
    }

    private static int req(Integer value) {
        return value == null ? 0 : value;
    }

    private static List<CountTier> buildCountTiers(RankSettingVO setting) {
        RankSettingVO current = setting == null ? defaultSettings() : setting;
        List<CountTier> tiers = new ArrayList<>();
        tiers.add(new CountTier("倔强青铜Ⅰ", "bronze", safe(current.getBronze1Count())));
        tiers.add(new CountTier("倔强青铜Ⅱ", "bronze", safe(current.getBronze2Count())));
        tiers.add(new CountTier("倔强青铜Ⅲ", "bronze", safe(current.getBronzeCount())));
        tiers.add(new CountTier("秩序白银Ⅰ", "silver", safe(current.getSilver1Count())));
        tiers.add(new CountTier("秩序白银Ⅱ", "silver", safe(current.getSilver2Count())));
        tiers.add(new CountTier("秩序白银Ⅲ", "silver", safe(current.getSilverCount())));
        tiers.add(new CountTier("荣耀黄金Ⅰ", "gold", safe(current.getGold1Count())));
        tiers.add(new CountTier("荣耀黄金Ⅱ", "gold", safe(current.getGold2Count())));
        tiers.add(new CountTier("荣耀黄金Ⅲ", "gold", safe(current.getGold3Count())));
        tiers.add(new CountTier("荣耀黄金Ⅳ", "gold", safe(current.getGoldCount())));
        tiers.add(new CountTier("尊贵铂金Ⅰ", "platinum", safe(current.getPlatinum1Count())));
        tiers.add(new CountTier("尊贵铂金Ⅱ", "platinum", safe(current.getPlatinum2Count())));
        tiers.add(new CountTier("尊贵铂金Ⅲ", "platinum", safe(current.getPlatinum3Count())));
        tiers.add(new CountTier("尊贵铂金Ⅳ", "platinum", safe(current.getPlatinumCount())));
        tiers.add(new CountTier("永恒钻石Ⅰ", "diamond", safe(current.getDiamond1Count())));
        tiers.add(new CountTier("永恒钻石Ⅱ", "diamond", safe(current.getDiamond2Count())));
        tiers.add(new CountTier("永恒钻石Ⅲ", "diamond", safe(current.getDiamond3Count())));
        tiers.add(new CountTier("永恒钻石Ⅳ", "diamond", safe(current.getDiamond4Count())));
        tiers.add(new CountTier("永恒钻石Ⅴ", "diamond", safe(current.getDiamondCount())));
        tiers.add(new CountTier("至尊星耀Ⅰ", "starry", safe(current.getStarry1Count())));
        tiers.add(new CountTier("至尊星耀Ⅱ", "starry", safe(current.getStarry2Count())));
        tiers.add(new CountTier("至尊星耀Ⅲ", "starry", safe(current.getStarry3Count())));
        tiers.add(new CountTier("至尊星耀Ⅳ", "starry", safe(current.getStarry4Count())));
        tiers.add(new CountTier("至尊星耀Ⅴ", "starry", safe(current.getStarryCount())));
        addStarTierGroup(tiers, "最强王者", "king", safe(current.getKingCount()), safe(current.getLegendCount()));
        addStarTierGroup(tiers, "无双王者", "legend", safe(current.getLegendCount()), safe(current.getGloryCount()));
        addFinalStarTierGroup(tiers, "荣耀王者", "glory", safe(current.getGloryCount()));
        tiers.sort((left, right) -> Integer.compare(right.threshold, left.threshold));
        return tiers;
    }

    private static void addStarTierGroup(List<CountTier> tiers, String baseTitle, String cssCode, int start, int nextStart) {
        if (start <= 0) {
            return;
        }
        tiers.add(new CountTier(baseTitle, cssCode, start));
        for (int threshold = start + 1; threshold < nextStart; threshold++) {
            tiers.add(new CountTier(baseTitle + (threshold - start) + "星", cssCode, threshold));
        }
    }

    private static void addFinalStarTierGroup(List<CountTier> tiers, String baseTitle, String cssCode, int start) {
        if (start <= 0) {
            return;
        }
        tiers.add(new CountTier(baseTitle, cssCode, start));
        for (int star = 30; star >= 1; star--) {
            tiers.add(new CountTier(baseTitle + star + "星", cssCode, start + star));
        }
    }

    private static class CountTier {
        private final String title;
        private final String cssCode;
        private final int threshold;

        private CountTier(String title, String cssCode, int threshold) {
            this.title = title;
            this.cssCode = cssCode;
            this.threshold = threshold;
        }
    }

    public static class RankResult {
        private final String title;
        private final String cssCode;

        public RankResult(String title, String cssCode) {
            this.title = title;
            this.cssCode = cssCode;
        }

        public String getTitle() {
            return title;
        }

        public String getCssCode() {
            return cssCode;
        }
    }
}
