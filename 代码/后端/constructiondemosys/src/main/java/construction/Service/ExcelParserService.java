package construction.Service;

import construction.entity.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.*;

@Service
public class ExcelParserService {

    private static final String HIERARCHY_COL_NAME = "Location Hierarchy";
    private static final int HEADER_ROW_IDX = 0;
    private static final int LOCATION_START_COL = 5; // 假设从 F 列开始

    public ProjectModelData parse(MultipartFile file) throws Exception {
        InputStream is = file.getInputStream();
        Workbook workbook = new XSSFWorkbook(is);
        Sheet sheet = workbook.getSheetAt(0);

        // ==========================================
        // 1. 动态定位列和行
        // ==========================================

        // A. 定位 Hierarchy 列
        int hierarchyColIdx = -1;
        Row headerRow = sheet.getRow(HEADER_ROW_IDX);
        if (headerRow == null) throw new RuntimeException("Excel header row is empty");

        for (Cell cell : headerRow) {
            if (getCellValue(cell).toLowerCase().contains(HIERARCHY_COL_NAME.toLowerCase())) {
                hierarchyColIdx = cell.getColumnIndex();
                break;
            }
        }
        // 没找到则默认最后一列
        if (hierarchyColIdx == -1) hierarchyColIdx = headerRow.getLastCellNum() - 1;

        // B. 动态确定 Location 层级行 和 Activity 开始行
        List<Integer> locationRowIndices = new ArrayList<>();
        List<String> levelNames = new ArrayList<>();

        // 从表头下一行开始扫描
        int currentRow = HEADER_ROW_IDX + 1;
        int activityStartRow = -1;
        int maxRow = sheet.getLastRowNum();

        while (currentRow <= maxRow) {
            Row row = sheet.getRow(currentRow);
            if (row == null) { currentRow++; continue; }

            String hierVal = getCellValue(row.getCell(hierarchyColIdx));
            String idVal = getCellValue(row.getCell(0)); // 检查第一列 ID

            // 如果 Hierarchy 列有值，且不是数字（排除干扰），则是层级定义行
            if (!hierVal.isEmpty() && !isNumeric(hierVal)) {
                locationRowIndices.add(currentRow);
                levelNames.add(hierVal);
            }
            // 如果 ID 列是数字 (例如 "1"), 说明数据行开始了，循环结束
            else if (isNumeric(idVal) && !idVal.isEmpty()) {
                activityStartRow = currentRow;
                break;
            }
            // 如果 Hierarchy 为空，且 ID 也不是数字，这通常是中间的 "叶子节点表头行" (B1, B2...)
            // 我们继续往下找数据

            currentRow++;
        }

        // 如果没找到明确的数字 ID 行，就假设层级结束后的第2行是数据（跳过叶子表头）
        if (activityStartRow == -1) {
            if (!locationRowIndices.isEmpty()) {
                activityStartRow = locationRowIndices.get(locationRowIndices.size() - 1) + 2;
            } else {
                activityStartRow = HEADER_ROW_IDX + 1; // 默认 fallback
            }
        }

        System.out.println("Dynamic Analysis: Hierarchy Levels = " + levelNames.size() + ", Data Starts Row = " + activityStartRow);

        // ==========================================
        // 2. 构建树形结构 (含 ffill 逻辑)
        // ==========================================
        List<LocationNode> rootNodes = new ArrayList<>();
        Map<String, LocationNode> nodeLookup = new HashMap<>();

        // 记录列号 -> 叶子节点 ID 的映射
        Map<Integer, String> colIndexToLeafId = new HashMap<>();
        // 记录上一行某列的值，用于 ffill
        Map<Integer, String> lastColValues = new HashMap<>();

        for (int c = LOCATION_START_COL; c < hierarchyColIdx; c++) {

            // 简单的列有效性检查：只处理层级行里有数据的列（包括合并单元格延伸）
            List<String> currentPath = new ArrayList<>();
            List<String> pathIds = new ArrayList<>();
            boolean colHasData = false;

            for (int rIdx : locationRowIndices) {
                Row row = sheet.getRow(rIdx);
                Cell cell = row.getCell(c);
                String val = getCellValue(cell);

                // --- ffill 逻辑 ---
                if (val.isEmpty()) {
                    if (lastColValues.containsKey(rIdx)) {
                        val = lastColValues.get(rIdx);
                    } else {
                        val = "Unknown";
                    }
                } else {
                    lastColValues.put(rIdx, val);
                    colHasData = true;
                }

                currentPath.add(val);
                pathIds.add(cleanId(val));
            }

            if (!colHasData && currentPath.stream().allMatch(s -> s.equals("Unknown"))) continue;

            // 建树
            List<LocationNode> currentLevelList = rootNodes;
            String parentPathId = "root";

            for (int i = 0; i < currentPath.size(); i++) {
                String name = currentPath.get(i);
                String cleanName = pathIds.get(i);

                // 构造 ID
                String uniqueId = (i == 0) ? ("loc_" + cleanName) : (parentPathId + "_" + cleanName);
                String typeName = (i < levelNames.size()) ? levelNames.get(i) : "Level" + i;

                // 查找或创建节点
                LocationNode existingNode = null;
                for (LocationNode node : currentLevelList) {
                    if (node.id.equals(uniqueId)) {
                        existingNode = node;
                        break;
                    }
                }

                if (existingNode == null) {
                    LocationNode newNode = new LocationNode();
                    newNode.id = uniqueId;
                    newNode.name = name;
                    newNode.type = typeName;
                    currentLevelList.add(newNode);
                    existingNode = newNode;
                }

                currentLevelList = existingNode.children;
                parentPathId = uniqueId;

                // 记录叶子映射
                if (i == currentPath.size() - 1) {
                    colIndexToLeafId.put(c, uniqueId);
                }
            }
        }

        // ==========================================
        // 3. 解析 Activities
        // ==========================================
        List<ActivityDTO> activities = new ArrayList<>();

        // 从动态计算出的 activityStartRow 开始读取
        for (int r = activityStartRow; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;

            String actName = getCellValue(row.getCell(1));
            // 如果 Activity Name 为空，可能是结束了
            if (actName.isEmpty()) continue;

            ActivityDTO act = new ActivityDTO();
            act.id = UUID.randomUUID().toString();
            act.name = actName;
            act.trade = getCellValue(row.getCell(2));
            act.crew = (int) getNumericValue(row.getCell(3));
            act.dailyProd = getNumericValue(row.getCell(4));
            act.quantities = new HashMap<>();

            // 读取数量
            for (Map.Entry<Integer, String> entry : colIndexToLeafId.entrySet()) {
                int colIdx = entry.getKey();
                String locId = entry.getValue();

                double qty = getNumericValue(row.getCell(colIdx));
                if (qty > 0) {
                    act.quantities.put(locId, qty);
                }
            }
            activities.add(act);
        }

        ProjectModelData modelData = new ProjectModelData();
        modelData.hierarchyLevels = levelNames;
        modelData.locationTree = rootNodes;
        modelData.activities = activities;

        return modelData;
    }

    // --- 辅助方法 ---
    private String getCellValue(Cell cell) {
        if (cell == null) return "";
        CellType type = cell.getCellType();
        if (type == CellType.FORMULA) type = cell.getCachedFormulaResultType();
        if (type == CellType.STRING) return cell.getStringCellValue().trim();
        if (type == CellType.NUMERIC) {
            if (DateUtil.isCellDateFormatted(cell)) return cell.getDateCellValue().toString();
            double val = cell.getNumericCellValue();
            if (val == (long) val) return String.valueOf((long) val);
            return String.valueOf(val);
        }
        return "";
    }

    private double getNumericValue(Cell cell) {
        if (cell == null) return 0.0;
        CellType type = cell.getCellType();
        if (type == CellType.FORMULA) type = cell.getCachedFormulaResultType();
        if (type == CellType.NUMERIC) return cell.getNumericCellValue();
        if (type == CellType.STRING) {
            try { return Double.parseDouble(cell.getStringCellValue()); } catch (Exception e) { return 0.0; }
        }
        return 0.0;
    }

    private String cleanId(String text) {
        if (text == null) return "null";
        return text.replaceAll("[^a-zA-Z0-9]", "").toLowerCase();
    }

    private boolean isNumeric(String str) {
        if (str == null || str.isEmpty()) return false;
        try { Double.parseDouble(str); return true; } catch (Exception e) { return false; }
    }
}