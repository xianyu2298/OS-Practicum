import java.io.*;
import java.util.*;

class PCB {
    String pName; // 进程名称
    String pRemark; // 备注
    String pStatus; // 状态
    int createTime; // 创建时间
    int runTime; // 运行时间
    int grade; // 优先级
    int startTime; // 开始时间
    int completeTime; // 完成时间
    int turnoverTime; // 周转时间
    double weightedTurnoverTime; // 带权周转时间
    int originalRunTime; // 原始运行时间
    PCB(String pName, int createTime, int runTime, int grade, String pRemark) {
        this.pName = pName;
        this.createTime = createTime;
        this.runTime = runTime;
        this.originalRunTime = runTime; // 记录原始运行时间
        this.grade = grade;
        this.pRemark = pRemark;
        this.pStatus = "等待";
        this.startTime = -1;
    }

}

public class CPUScheduling {

    private static final List<PCB> processList = new ArrayList<>();
    private static double pageSize = 2.2;
    private static int timeSlice = 25;



    static class PageManager {
        private final double pageSize;
        private final int maxPages;
        private final LinkedList<Integer> fifoPages;
        private final Map<Integer, Integer> lruPages;
        private final List<String> log; // 存储页面置换的日志记录
        private int pageFaults; // 页面错误次数
        private int pageHits; // 页面命中次数

        PageManager(double pageSize, int maxPages) {
            this.pageSize = pageSize;
            this.maxPages = maxPages;
            this.fifoPages = new LinkedList<>();
            this.lruPages = new HashMap<>();
            this.log = new ArrayList<>();
            this.pageFaults = 0;
            this.pageHits = 0;
        }

        public void fifoReplace(int page) {
            if (fifoPages.contains(page)) {
                pageHits++;
                log.add("FIFO: 页面 " + page + " 已经在内存中 (命中)");
                displayMemoryState(); // 实时显示内存状态
                return;
            }

            pageFaults++;
            if (fifoPages.size() >= maxPages) {
                int removed = fifoPages.removeFirst();
                log.add("FIFO: 页面 " + removed + " 被移除");
            }
            fifoPages.add(page);
            log.add("FIFO: 页面 " + page + " 被加载");
            displayMemoryState(); // 实时显示内存状态
        }

        public void lruReplace(int page, int currentTime) {
            if (lruPages.containsKey(page)) {
                pageHits++;
                lruPages.put(page, currentTime); // 更新页面最近使用时间
                log.add("LRU: 页面 " + page + " 已经在内存中 (命中)");
                displayMemoryState(); // 实时显示内存状态
                return;
            }

            pageFaults++;
            if (lruPages.size() >= maxPages) {
                int lruPage = Collections.min(lruPages.entrySet(), Map.Entry.comparingByValue()).getKey();
                lruPages.remove(lruPage);
                log.add("LRU: 页面 " + lruPage + " 被移除");
            }
            lruPages.put(page, currentTime);
            log.add("LRU: 页面 " + page + " 被加载");
            displayMemoryState(); // 实时显示内存状态
        }

        public List<String> getLog() {
            return log;
        }

        public int getPageFaults() {
            return pageFaults;
        }

        public int getPageHits() {
            return pageHits;
        }

        public double getHitRate() {
            return (pageHits + pageFaults) == 0 ? 0 : (double) pageHits / (pageHits + pageFaults);
        }

        public void displayMemoryState() {
            System.out.println("当前内存状态:");
            System.out.print("|");

            if (!fifoPages.isEmpty()) { // 如果是 FIFO 算法，使用 fifoPages
                for (int page : fifoPages) {
                    System.out.printf(" %d |", page);
                }
            } else if (!lruPages.isEmpty()) { // 如果是 LRU 算法，使用 lruPages 的 keySet()
                for (int page : lruPages.keySet()) {
                    System.out.printf(" %d |", page);
                }
            }

            System.out.println();
        }


    }


    public static void loadProcesses(Map<String, Integer> runTimes) {
        try (BufferedReader reader = new BufferedReader(new FileReader("Process.txt"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split("\t");
                if (parts.length < 4) {
                    System.err.println("无效的行格式: " + line);
                    continue;
                }
                try {
                    String pName = parts[0].trim(); // 进程名
                    int createTime = Integer.parseInt(parts[1].trim()); // 创建时间
                    int grade = Integer.parseInt(parts[2].trim()); // 优先级
                    String pRemark = parts[3].trim(); // 备注（程序名）

                    // 标准化程序名以匹配 runTimes 中的键
                    String standardizedProgramName = pRemark.replace("程序", "") + "程序"; // 将"程序A"标准化为"A程序"

                    // 从 runTimes 中获取运行时间，如果没有，则设置为默认值 10
                    if (!runTimes.containsKey(standardizedProgramName)) {
                        System.err.printf("警告: 未找到程序 %s 的运行时间，使用默认值 10\n", standardizedProgramName);
                    }
                    int runTime = runTimes.getOrDefault(standardizedProgramName, 10);

                    // 添加进程到列表
                    processList.add(new PCB(pName, createTime, runTime, grade, pRemark));
                } catch (NumberFormatException e) {
                    System.err.println("数字解析失败: " + line + " - 错误信息: " + e.getMessage());
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    public static Map<String, Map<String, Double>> loadPrograms() {
        Map<String, Map<String, Double>> programs = new HashMap<>();
        try (BufferedReader reader = new BufferedReader(new FileReader("program.txt"))) {
            String line;
            String currentProgram = null;
            Map<String, Double> functions = null;

            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.startsWith("文件名")) {
                    if (currentProgram != null && functions != null) {
                        programs.put(currentProgram, functions);
                    }
                    String[] parts = line.split(" +", 2);
                    if (parts.length > 1) {
                        currentProgram = parts[1];
                        functions = new HashMap<>();
                    } else {
                        System.err.println("无效的程序名行格式: " + line);
                        currentProgram = null;
                        functions = null;
                    }
                } else if (!line.isEmpty() && functions != null) {
                    String[] parts = line.split(" +");
                    if (parts.length > 1) {
                        try {
                            String functionName = parts[0];
                            double functionSize = Double.parseDouble(parts[1]);
                            functions.put(functionName, functionSize);
                        } catch (NumberFormatException e) {
                            System.err.println("数字解析失败: " + line + " - 错误信息: " + e.getMessage());
                        }
                    } else {
                        System.err.println("无效的函数行格式: " + line);
                    }
                }
            }
            if (currentProgram != null && functions != null) {
                programs.put(currentProgram, functions);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return programs;
    }

    public static Map<String, Integer> loadRunSteps() {
        Map<String, Integer> runTimes = new HashMap<>(); // 存储每个程序的运行时间

        try (BufferedReader reader = new BufferedReader(new FileReader("run.txt"))) {
            String line;
            String currentProgram = null; // 当前程序名
            int maxTime = 0; // 当前程序的最大时间

            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.startsWith("程序名")) { // 检测程序名
                    if (currentProgram != null) {
                        runTimes.put(currentProgram, maxTime); // 保存当前程序的最大时间
                    }
                    String[] parts = line.split("\\s+", 2);
                    if (parts.length > 1) {
                        currentProgram = parts[1].trim();
                        currentProgram = currentProgram.replace("程序", "") + "程序"; // 标准化程序名
                        maxTime = 0; // 重置最大时间
                    }
                } else if (!line.isEmpty() && currentProgram != null) {
                    // 解析关键时间点
                    String[] parts = line.split("\\s+");
                    try {
                        int time = Integer.parseInt(parts[0]); // 时间点在第一列
                        maxTime = Math.max(maxTime, time); // 更新最大时间
                    } catch (NumberFormatException e) {
                        System.err.println("关键时间点解析失败: " + line);
                    }
                }
            }

            // 保存最后一个程序的最大时间
            if (currentProgram != null) {
                runTimes.put(currentProgram, maxTime);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return runTimes;
    }

    public static void fcfsScheduling() {
        clearResultFile();
        processList.sort(Comparator.comparingInt(p -> p.createTime));
        int currentTime = 0;
        for (PCB process : processList) {
            if (currentTime < process.createTime) {
                currentTime = process.createTime;
            }
            process.startTime = currentTime;
            process.completeTime = currentTime + process.runTime;
            process.turnoverTime = process.completeTime - process.createTime;
            process.weightedTurnoverTime = (double) process.turnoverTime / process.runTime;
            currentTime += process.runTime;
        }
        saveResults("FCFS");
    }


    public static void rrScheduling() {
        clearResultFile();
        Queue<PCB> queue = new LinkedList<>();
        processList.sort(Comparator.comparingInt(p -> p.createTime)); // 按创建时间排序
        int currentTime = 0;
        int index = 0;

        Map<PCB, Integer> remainingTimeMap = new HashMap<>();
        for (PCB process : processList) {
            remainingTimeMap.put(process, process.runTime);
        }

        while (!queue.isEmpty() || index < processList.size()) {
            // 等待新进程到达
            if (queue.isEmpty() && index < processList.size() && processList.get(index).createTime > currentTime) {
                currentTime = processList.get(index).createTime;
            }

            // 加入当前时间之前到达的所有新进程
            while (index < processList.size() && processList.get(index).createTime <= currentTime) {
                PCB newProcess = processList.get(index);
                if (!queue.contains(newProcess)) { // 避免重复加入
                    queue.offer(newProcess); // 加入队列末尾
                }
                index++;
            }

            // 如果队列仍为空，说明没有可调度进程，时间推进
            if (queue.isEmpty()) {
                currentTime++;
                continue;
            }

            // 从队列中获取第一个进程
            PCB process = queue.poll();

            // 设置进程的首次运行时间
            if (process.startTime == -1) {
                process.startTime = currentTime;
            }

            // 计算当前时间片内的执行
            int remainingTime = remainingTimeMap.get(process);
            int executionTime = Math.min(timeSlice, remainingTime);
            remainingTime -= executionTime;
            currentTime += executionTime;

            // 如果进程还有剩余时间，重新加入队列末尾
            if (remainingTime > 0) {
                remainingTimeMap.put(process, remainingTime);
                queue.offer(process); // 放到队列末尾
            } else {
                // 如果进程完成，更新完成时间等信息
                remainingTimeMap.remove(process);
                process.completeTime = currentTime;
                process.turnoverTime = process.completeTime - process.createTime;
                process.weightedTurnoverTime = (double) process.turnoverTime / process.originalRunTime;
            }

            // 打印调试信息
            System.out.printf("当前时间: %d | 执行进程: %s | 剩余时间: %d\n", currentTime, process.pName, remainingTime);
            System.out.print("当前队列状态（执行后）: ");
            queue.forEach(p -> System.out.print(p.pName + " "));
            System.out.println();
        }

        saveResults("RR");
    }

    public static void saveResults(String schedulingType) {
        for (PCB process : processList) {
            if (process.startTime == -1 || process.completeTime == 0) {
                throw new IllegalStateException("进程 " + process.pName + " 的调度结果不完整！");
            }
        }

        try (BufferedWriter writer = new BufferedWriter(new FileWriter("result.txt"))) {
            writer.write(schedulingType + " 调度结果:\n");
            writer.write("进程名\t创建时间\t开始时间\t完成时间\t运行时间\t周转时间\t带权周转时间\n");
            for (PCB process : processList) {
                writer.write(String.format("%s\t%d\t%d\t%d\t%d\t%d\t%.2f\n",
                        process.pName, process.createTime, process.startTime, process.completeTime,
                        process.runTime, process.turnoverTime, process.weightedTurnoverTime));
            }
            System.out.println(schedulingType + " 调度结果已保存到 result.txt 文件。");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // 新增方法：根据程序大小计算页面需求
    public static Map<String, Integer> calculatePageRequirements(Map<String, Map<String, Double>> programs, double pageSize) {
        Map<String, Integer> pageRequirements = new HashMap<>();
        for (Map.Entry<String, Map<String, Double>> entry : programs.entrySet()) {
            String programName = entry.getKey();
            double totalSize = entry.getValue().values().stream().mapToDouble(Double::doubleValue).sum(); // 累加函数大小
            int pages = (int) Math.ceil(totalSize / pageSize); // 根据页面大小计算需要的页面数
            pageRequirements.put(programName, pages);
        }
        return pageRequirements;
    }

    // 修改主方法，调用分页调度机制
    public static void pageScheduling(Map<String, Map<String, Double>> programs) {
        System.out.println("加载程序页面需求...");
        Map<String, Integer> pageRequirements = calculatePageRequirements(programs, pageSize);

        System.out.println("请输入每个进程的最大页面数:");
        Scanner scanner = new Scanner(System.in);
        int maxPages = scanner.nextInt(); // 用户动态设置最大页面数

        System.out.println("请输入页面调度算法 (1. FIFO  2. LRU):");
        int choice = scanner.nextInt();

        PageManager pageManager = new PageManager(pageSize, maxPages); // 使用动态页面数

        System.out.println("页面调度过程:");
        int currentTime = 0;

        for (Map.Entry<String, Integer> entry : pageRequirements.entrySet()) {
            String programName = entry.getKey();
            int pages = entry.getValue();
            System.out.printf("程序 %s 需要 %d 页\n", programName, pages);

            for (int page = 0; page < pages; page++) {
                if (choice == 1) {
                    pageManager.fifoReplace(page); // 使用 FIFO 算法
                } else {
                    pageManager.lruReplace(page, currentTime); // 使用 LRU 算法
                }
                currentTime++;
            }
        }

        // 输出调度日志
        System.out.println("\n页面置换日志:");
        for (String logEntry : pageManager.getLog()) {
            System.out.println(logEntry);
        }

        // 输出分页调度总结报告
        displayPageSummary(pageManager, pageRequirements);
    }

    public static void displayPageSummary(PageManager pageManager, Map<String, Integer> pageRequirements) {
        System.out.println("\n分页调度总结报告:");
        for (Map.Entry<String, Integer> entry : pageRequirements.entrySet()) {
            String programName = entry.getKey();
            int pages = entry.getValue();
            System.out.printf("程序: %s | 总页面数: %d\n", programName, pages);
        }
        System.out.printf("页面命中次数: %d\n", pageManager.getPageHits());
        System.out.printf("页面置换次数 (页面错误): %d\n", pageManager.getPageFaults());
        System.out.printf("页面命中率: %.2f%%\n", pageManager.getHitRate() * 100);
    }

    public static void clearResultFile() {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter("result.txt"))) {
            // 清空文件内容，只创建空文件
            writer.write("");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // 新增方法：动态模拟 CPU 占用情况
    public static void simulateCPU(Map<String, Integer> runTimes) {
        try (BufferedReader reader = new BufferedReader(new FileReader("run.txt"))) {
            String line;
            String currentProgram = null;
            Map<Integer, String> cpuLog = new TreeMap<>();

            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.startsWith("程序名")) {
                    currentProgram = line.split("\\s+")[1];
                } else if (!line.isEmpty() && currentProgram != null) {
                    String[] parts = line.split("\\s+");
                    int time = Integer.parseInt(parts[0]);
                    String operation = parts[1];
                    cpuLog.put(time, "程序 " + currentProgram + ": " + operation);
                }
            }

            System.out.println("动态模拟 CPU 占用情况...");
            for (Map.Entry<Integer, String> entry : cpuLog.entrySet()) {
                Thread.sleep(1); // 模拟每 1ms 刷新
                System.out.printf("时间: %dms | %s\n", entry.getKey(), entry.getValue());
            }
            System.out.println("CPU 占用情况模拟完成！");
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }



    public static void main(String[] args) {
        System.out.println("加载程序执行步骤并计算运行时间...");
        Map<String, Integer> runTimes = loadRunSteps(); // 从 run.txt 中计算程序运行时间
        System.out.println("程序执行步骤加载完成！\n");

        System.out.println("加载进程信息...");
        loadProcesses(runTimes); // 加载进程信息并设置运行时间
        System.out.println("进程信息加载完成！\n");

        System.out.println("加载程序信息...");
        Map<String, Map<String, Double>> programs = loadPrograms();
        System.out.println("程序信息加载完成！\n");

        Scanner scanner = new Scanner(System.in);

        while (true) {
            System.out.println("选择功能:");
            System.out.println("1. 查看进程信息\n2. 查看程序详细信息\n3. 查看程序执行步骤\n4. 先来先服务调度\n5. 时间片轮转调度");
            System.out.println("6. 设置页面大小和时间片长度\n7. 退出\n8. 动态模拟 CPU 占用");
            int choice = scanner.nextInt();

            switch (choice) {
                case 1:
                    // 显示进程信息
                    System.out.println("进程信息:");
                    for (PCB pcb : processList) {
                        System.out.printf("进程: %s | 创建时间: %d | 优先级: %d | 运行时间: %d | 备注: %s\n",
                                pcb.pName, pcb.createTime, pcb.grade, pcb.runTime, pcb.pRemark);
                    }
                    break;

                case 2:
                    // 显示程序信息
                    System.out.println("程序信息:");
                    for (Map.Entry<String, Map<String, Double>> entry : programs.entrySet()) {
                        System.out.println("程序名: " + entry.getKey());
                        for (Map.Entry<String, Double> func : entry.getValue().entrySet()) {
                            System.out.printf("  函数: %s | 大小: %.2f KB\n", func.getKey(), func.getValue());
                        }
                    }
                    break;

                case 3:
                    // 显示程序执行步骤
                    System.out.println("程序执行步骤:");
                    for (Map.Entry<String, Integer> entry : runTimes.entrySet()) {
                        System.out.println("程序名: " + entry.getKey() + " | 运行时间: " + entry.getValue());
                    }
                    break;

                case 4:
                    // 先来先服务调度
                    fcfsScheduling();
                    System.out.println("FCFS调度已完成，结果已保存到 result.txt");
                    break;

                case 5:
                    // 时间片轮转调度
                    rrScheduling();
                    System.out.println("RR调度已完成，结果已保存到 result.txt");
                    break;

                case 6:
                    // 设置页面大小和时间片长度，或执行分页调度
                    System.out.println("1. 设置页面大小和时间片长度\n2. 执行分页调度");
                    int option = scanner.nextInt();
                    if (option == 1) {
                        System.out.println("请输入新的页面大小 (单位: KB):");
                        pageSize = scanner.nextDouble();
                        System.out.println("请输入新的时间片长度 (单位: ms):");
                        timeSlice = scanner.nextInt();
                        System.out.printf("页面大小已设置为: %.2f KB | 时间片长度已设置为: %d ms\n", pageSize, timeSlice);
                    } else if (option == 2) {
                        pageScheduling(programs);
                    }
                    break;

                case 7:
                    // 退出程序
                    System.out.println("退出程序...");
                    scanner.close();
                    return;

                case 8:
                    // 动态模拟 CPU 占用情况
                    simulateCPU(runTimes);
                    break;

                default:
                    System.out.println("无效选择，请重新输入！");
            }
        }
    }

}