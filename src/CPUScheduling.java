import java.io.*;
import java.util.*;

class PCB {
    String pName; // 进程名称
    String pRemark; // 备注
    String pStatus; // 进程状态
    int createTime; // 创建时间
    int runTime; // 运行时间
    int grade; // 优先级
    int startTime; // 开始时间
    int completeTime; // 完成时间
    int turnoverTime; // 周转时间
    double weightedTurnoverTime; // 带权周转时间
    int originalRunTime; // 原始运行时间，记录进程的初始运行时间，用于计算周转时间和调度
    PCB(String pName, int createTime, int runTime, int grade, String pRemark) {
        this.pName = pName;
        this.createTime = createTime;
        this.runTime = runTime;
        this.originalRunTime = runTime;
        this.grade = grade;
        this.pRemark = pRemark;
        this.pStatus = "等待"; // 初始化时将进程状态设置为“等待”
        this.startTime = -1; // 初始化时未开始运行，将开始时间设为 -1（表示未设置）
    }
}

public class CPUScheduling {
    private static final List<PCB> processList = new ArrayList<>();// 存储所有进程的列表
    private static double pageSize = 2.2;// 默认页面大小，单位 KB
    private static int timeSlice = 25;// 默认时间片长度，单位 ms
    static class PageManager {
        private final double pageSize; // 页面大小
        private final int maxPages; // 最大页面数量
        private final LinkedList<Integer> fifoPages; // 用于FIFO页面置换算法的链表
        private final Map<Integer, Integer> lruPages; // 用于LRU页面置换算法的映射
        private final List<String> log; // 存储页面置换的日志记录
        private int pageFaults; // 页面错误次数（缺页次数）
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

        // FIFO页面置换算法
        public void fifoReplace(int page) {
            // 如果页面已经在内存中，命中次数加1，记录命中日志
            if (fifoPages.contains(page)) {
                pageHits++;
                log.add("FIFO: 页面 " + page + " 已经在内存中 (命中)");
                displayMemoryState(); // 实时显示内存状态
                return;
            }
            // 页面不在内存中，发生缺页
            pageFaults++;
            if (fifoPages.size() >= maxPages) {
                // 如果内存已满，移除最先进入的页面（FIFO）
                int removed = fifoPages.removeFirst();
                log.add("FIFO: 页面 " + removed + " 被移除");
            }
            // 将新页面加载到内存中
            fifoPages.add(page);
            log.add("FIFO: 页面 " + page + " 被加载");
            displayMemoryState(); // 实时显示内存状态
        }

        // LRU页面置换算法
        public void lruReplace(int page, int currentTime) {
            // 如果页面已经在内存中，命中次数加1，更新最近使用时间
            if (lruPages.containsKey(page)) {
                pageHits++;
                lruPages.put(page, currentTime); // 更新页面最近使用时间
                log.add("LRU: 页面 " + page + " 已经在内存中 (命中)");
                displayMemoryState(); // 实时显示内存状态
                return;
            }
            // 页面不在内存中，发生缺页
            pageFaults++;
            if (lruPages.size() >= maxPages) {
                // 如果内存已满，移除最近最少使用的页面
                int lruPage = Collections.min(lruPages.entrySet(), Map.Entry.comparingByValue()).getKey();
                lruPages.remove(lruPage);
                log.add("LRU: 页面 " + lruPage + " 被移除");
            }
            // 将新页面加载到内存中，记录当前时间
            lruPages.put(page, currentTime);
            log.add("LRU: 页面 " + page + " 被加载");
            displayMemoryState(); // 实时显示内存状态
        }

        // 返回日志记录
        public List<String> getLog() {
            return log;
        }

        // 获取页面错误次数
        public int getPageFaults() {
            return pageFaults;
        }

        // 获取页面命中次数
        public int getPageHits() {
            return pageHits;
        }

        // 计算页面命中率
        public double getHitRate() {
            return (pageHits + pageFaults) == 0 ? 0 : (double) pageHits / (pageHits + pageFaults);
        }

        // 显示当前内存状态
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

    // 加载进程信息，从文件读取并创建PCB对象
    public static void loadProcesses(Map<String, Integer> runTimes) {
        try (BufferedReader reader = new BufferedReader(new FileReader("Process.txt"))) {
            String line; // 用于存储每行读取的内容
            while ((line = reader.readLine()) != null) {
                // 将当前行按照制表符（\t）分割成多个部分
                String[] parts = line.split("\t");
                // 如果当前行的分割部分少于 4，则说明格式不符合要求，打印错误信息并跳过
                if (parts.length < 4) {
                    System.err.println("无效的行格式: " + line);
                    continue; // 处理下一行
                }

                // 使用 try 块处理行中可能出现的数字解析异常
                try {
                    // 从分割后的数组中提取各个字段
                    String pName = parts[0].trim(); // 进程名，去除首尾空格
                    int createTime = Integer.parseInt(parts[1].trim()); // 创建时间，解析为整数
                    int grade = Integer.parseInt(parts[2].trim()); // 优先级，解析为整数
                    String pRemark = parts[3].trim(); // 备注（程序名），去除首尾空格

                    // 标准化程序名，将“程序A”转换为“A程序”以便匹配 runTimes 键
                    String standardizedProgramName = pRemark.replace("程序", "") + "程序";

                    // 从 runTimes Map 中查找标准化程序名对应的运行时间
                    // 如果未找到对应键，打印警告并使用默认运行时间 10
                    if (!runTimes.containsKey(standardizedProgramName)) {
                        System.err.printf("警告: 未找到程序 %s 的运行时间，使用默认值 10\n", standardizedProgramName);
                    }
                    int runTime = runTimes.getOrDefault(standardizedProgramName, 10); // 获取运行时间或使用默认值

                    // 创建一个新的 PCB 对象（表示一个进程）并添加到 processList 列表中
                    processList.add(new PCB(pName, createTime, runTime, grade, pRemark));

                } catch (NumberFormatException e) {
                    // 如果某个数字字段解析失败，打印错误信息和出错行
                    System.err.println("数字解析失败: " + line + " - 错误信息: " + e.getMessage());
                }
            }
        } catch (IOException e) {
            // 如果文件读取过程中出现 IO 异常，打印堆栈信息
            e.printStackTrace();
        }
    }


    //加载程序
    public static Map<String, Map<String, Double>> loadPrograms() {
        // 定义一个键值对，用于存储所有程序和其对应的函数及大小
        // 外层 Map 的 key 是程序名，value 是一个 Map，该 Map 的 key 是函数名，value 是函数的大小
        Map<String, Map<String, Double>> programs = new HashMap<>();
        try (BufferedReader reader = new BufferedReader(new FileReader("program.txt"))) {
            String line; // 每次读取的文件行
            String currentProgram = null; // 当前程序名
            Map<String, Double> functions = null; // 当前程序的函数名和大小映射

            // 循环逐行读取文件内容
            while ((line = reader.readLine()) != null) {
                // 去掉当前行的首尾空白字符
                line = line.trim();

                // 如果当前行是程序名的定义（以 "文件名" 开头）
                if (line.startsWith("文件名")) {
                    // 如果已经读取了一个程序的函数列表，将其保存到主 Map 中
                    if (currentProgram != null && functions != null) {
                        programs.put(currentProgram, functions); // 将当前程序及其函数列表存入主 Map
                    }

                    // 解析当前程序名
                    String[] parts = line.split(" +", 2); // 使用正则表达式分割，最多分为两部分
                    if (parts.length > 1) {
                        currentProgram = parts[1]; // 第二部分是程序名
                        functions = new HashMap<>(); // 初始化函数列表
                    } else {
                        // 如果没有足够的字段，打印错误信息并将程序名和函数列表置为空
                        System.err.println("无效的程序名行格式: " + line);
                        currentProgram = null;
                        functions = null;
                    }
                }
                // 如果当前行不是空行，并且已经解析了一个程序，则解析函数名和大小
                else if (!line.isEmpty() && functions != null) {
                    // 按空白字符分割当前行
                    String[] parts = line.split(" +");
                    if (parts.length > 1) {
                        try {
                            // 第一个字段是函数名
                            String functionName = parts[0];
                            // 第二个字段是函数大小（单位：KB）
                            double functionSize = Double.parseDouble(parts[1]); // 将字符串解析为 double 类型
                            functions.put(functionName, functionSize); // 将函数名和大小存入当前程序的函数列表
                        } catch (NumberFormatException e) {
                            // 如果函数大小解析失败，打印错误信息
                            System.err.println("数字解析失败: " + line + " - 错误信息: " + e.getMessage());
                        }
                    } else {
                        // 如果行的格式无效，打印错误信息
                        System.err.println("无效的函数行格式: " + line);
                    }
                }
            }

            // 在文件读取完成后，将最后一个程序及其函数列表存入主 Map
            if (currentProgram != null && functions != null) {
                programs.put(currentProgram, functions);
            }

        } catch (IOException e) {
            // 捕获文件读取过程中的 IO 异常并打印堆栈信息
            e.printStackTrace();
        }
        return programs;
    }

    public static Map<String, Integer> loadRunSteps() {
        // 定义一个 Map，用于存储每个程序及其对应的运行时间
        Map<String, Integer> runTimes = new HashMap<>();
        try (BufferedReader reader = new BufferedReader(new FileReader("run.txt"))) {
            String line; // 用于存储每次读取的一行数据
            String currentProgram = null; // 当前正在解析的程序名
            int maxTime = 0; // 当前程序的最大运行时间
            // 循环逐行读取文件内容
            while ((line = reader.readLine()) != null) {
                // 去掉行首和行尾的空白字符
                line = line.trim();
                // 检查当前行是否以 "程序名" 开头，表示该行定义了一个新的程序
                if (line.startsWith("程序名")) {
                    // 如果当前已经解析了一个程序，先保存其最大时间到 Map 中
                    if (currentProgram != null) {
                        runTimes.put(currentProgram, maxTime); // 将程序名和对应的最大运行时间存入 Map
                    }
                    // 解析新程序名
                    String[] parts = line.split("\\s+", 2); // 按空白字符分割，最多分成两部分
                    if (parts.length > 1) {
                        currentProgram = parts[1].trim(); // 获取程序名部分并去除空格
                        currentProgram = currentProgram.replace("程序", "") + "程序"; // 标准化程序名格式（如："程序A" 转为 "A程序"）
                        maxTime = 0; // 初始化最大运行时间为 0
                    }
                }
                // 如果当前行不是空行，并且程序名已被解析，则解析时间点
                else if (!line.isEmpty() && currentProgram != null) {
                    // 按空白字符分割当前行
                    String[] parts = line.split("\\s+");
                    try {
                        // 假定第一列是时间点，将其解析为整数
                        int time = Integer.parseInt(parts[0]);
                        // 更新最大运行时间，如果当前时间点比之前记录的最大时间更大
                        maxTime = Math.max(maxTime, time);
                    } catch (NumberFormatException e) {
                        System.err.println("关键时间点解析失败: " + line);
                    }
                }
            }
            // 文件读取完成后，保存最后一个程序的最大时间到 Map 中
            if (currentProgram != null) {
                runTimes.put(currentProgram, maxTime);
            }
        } catch (IOException e) {
            // 捕获文件读取过程中的 IO 异常并打印堆栈信息
            e.printStackTrace();
        }
        return runTimes;
    }


    public static void fcfsScheduling() {
        clearResultFile();

        // 将进程列表按创建时间升序排序，确保先到的进程先被调度
        processList.sort(Comparator.comparingInt(p -> p.createTime));

        // 初始化当前时间为 0，表示调度器的初始时间
        int currentTime = 0;

        // 遍历所有进程，按先来先服务的顺序依次调度
        for (PCB process : processList) {
            // 如果当前时间小于进程的创建时间，说明当前时间点还没有该进程，需要等待
            if (currentTime < process.createTime) {
                currentTime = process.createTime; // 将当前时间推进到该进程的创建时间
            }

            // 设置进程的开始时间为当前时间
            process.startTime = currentTime;

            // 计算进程的完成时间：当前时间加上进程的运行时间
            process.completeTime = currentTime + process.runTime;

            // 计算周转时间：完成时间减去创建时间
            process.turnoverTime = process.completeTime - process.createTime;

            // 计算带权周转时间：周转时间除以运行时间
            process.weightedTurnoverTime = (double) process.turnoverTime / process.runTime;

            // 输出当前的状态
            System.out.printf("当前时间: %d | 执行进程: %s | 剩余时间: %d\n",
                    currentTime, process.pName, process.runTime);

            // 更新当前时间：当前时间加上该进程的运行时间，表示调度器的时间推进
            currentTime += process.runTime;

            // 输出当前队列状态（剩余的进程）
            System.out.print("当前队列状态（执行后）: ");
            for (PCB p : processList) {
                if (p.startTime == -1) { // 仅显示尚未开始运行的进程
                    System.out.print(p.pName + " ");
                }
            }
            System.out.println();

        }
        saveResults("FCFS");
    }



    public static void rrScheduling() {
        clearResultFile();
        // 使用队列保存正在运行的进程，用于实现时间片轮转调度
        Queue<PCB> queue = new LinkedList<>();
        // 将进程列表按创建时间升序排序，确保进程按到达时间加入队列
        processList.sort(Comparator.comparingInt(p -> p.createTime));

        // 初始化当前时间为 0，表示调度器从时间点 0 开始
        int currentTime = 0;

        // 初始化索引，用于遍历进程列表
        int index = 0;

        // 创建一个映射表，用于记录每个进程的剩余运行时间
        Map<PCB, Integer> remainingTimeMap = new HashMap<>();
        for (PCB process : processList) {
            remainingTimeMap.put(process, process.runTime); // 初始化时，剩余时间等于总运行时间
        }

        // 开始轮转调度，直到队列为空且所有进程都已调度完毕
        while (!queue.isEmpty() || index < processList.size()) {

            // 如果队列为空，但有进程未到达当前时间，推进当前时间至下一个进程的创建时间
            if (queue.isEmpty() && index < processList.size() && processList.get(index).createTime > currentTime) {
                currentTime = processList.get(index).createTime;
            }

            // 将当前时间之前到达的所有进程加入队列
            while (index < processList.size() && processList.get(index).createTime <= currentTime) {
                PCB newProcess = processList.get(index);
                if (!queue.contains(newProcess)) { // 避免重复加入
                    queue.offer(newProcess); // 将新进程加入队列末尾
                }
                index++;
            }

            // 如果队列仍为空，说明当前时间点没有可调度进程，时间推进
            if (queue.isEmpty()) {
                currentTime++;
                continue; // 跳过当前循环，等待下一个时间点
            }

            // 从队列中取出第一个进程进行调度
            PCB process = queue.poll();

            // 如果这是进程第一次运行，则设置其开始时间为当前时间
            if (process.startTime == -1) {
                process.startTime = currentTime;
            }

            // 获取进程的剩余时间
            int remainingTime = remainingTimeMap.get(process);

            // 计算该进程在当前时间片内可以执行的时间
            int executionTime = Math.min(timeSlice, remainingTime);

            // 更新进程的剩余时间
            remainingTime -= executionTime;

            // 更新当前时间
            currentTime += executionTime;

            // 如果进程还有剩余时间，将其重新加入队列末尾
            if (remainingTime > 0) {
                remainingTimeMap.put(process, remainingTime); // 更新映射表中的剩余时间
                queue.offer(process); // 将进程重新加入队列末尾
            } else {
                // 如果进程运行完毕，更新其完成时间、周转时间和带权周转时间
                remainingTimeMap.remove(process); // 从映射表中移除该进程
                process.completeTime = currentTime; // 设置完成时间
                process.turnoverTime = process.completeTime - process.createTime; // 计算周转时间
                process.weightedTurnoverTime = (double) process.turnoverTime / process.originalRunTime; // 计算带权周转时间
            }
            System.out.printf("当前时间: %d | 执行进程: %s | 剩余时间: %d\n", currentTime, process.pName, remainingTime);
            System.out.print("当前队列状态（执行后）: ");
            queue.forEach(p -> System.out.print(p.pName + " ")); // 打印队列中的进程
            System.out.println(); // 换行
        }
        saveResults("RR");
    }

    public static void saveResults(String schedulingType) {
        // 检查所有进程的调度结果是否完整
        for (PCB process : processList) {
            // 如果某个进程的开始时间或完成时间未设置，则抛出异常
            if (process.startTime == -1 || process.completeTime == 0) {
                throw new IllegalStateException("进程 " + process.pName + " 的调度结果不完整！");
            }
        }
        try (BufferedWriter writer = new BufferedWriter(new FileWriter("result.txt"))) {
            // 写入调度类型的标题，例如 "FCFS 调度结果:"
            writer.write(schedulingType + " 调度结果:\n");

            // 写入表头信息
            writer.write("进程名\t创建时间\t开始时间\t完成时间\t运行时间\t周转时间\t带权周转时间\n");

            // 遍历所有进程，并写入它们的调度结果
            for (PCB process : processList) {
                writer.write(String.format("%s\t%d\t%d\t%d\t%d\t%d\t%.2f\n",
                        process.pName,
                        process.createTime,
                        process.startTime,
                        process.completeTime,
                        process.runTime,
                        process.turnoverTime,
                        process.weightedTurnoverTime
                ));
            }
            // 在控制台输出调试信息，表示调度结果已成功保存
            System.out.println(schedulingType + " 调度结果已保存到 result.txt 文件。");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    public static Map<String, Integer> calculatePageRequirements(Map<String, Map<String, Double>> programs, double pageSize) {
        // 创建一个 Map 来存储每个程序的页面需求
        Map<String, Integer> pageRequirements = new HashMap<>();

        // 遍历程序 Map 的每个条目
        for (Map.Entry<String, Map<String, Double>> entry : programs.entrySet()) {
            String programName = entry.getKey(); // 获取程序名
            // 计算该程序所有函数的总大小（使用 Stream API 进行累加）
            double totalSize = entry.getValue()
                    .values() // 获取所有函数大小的值集合
                    .stream() // 将其转换为流
                    .mapToDouble(Double::doubleValue) // 转换为 double 流
                    .sum(); // 计算总和

            // 根据页面大小计算所需的页面数，并向上取整
            int pages = (int) Math.ceil(totalSize / pageSize);

            // 将程序名和计算出的页面数放入结果 Map 中
            pageRequirements.put(programName, pages);
        }
        // 返回所有程序的页面需求
        return pageRequirements;
    }


    public static void pageScheduling(Map<String, Map<String, Double>> programs) {
        //包含程序及其函数大小的 Map。键为程序名，值为另一个 Map，其中键为函数名，值为函数大小
        // 输出提示信息，告知正在加载程序页面需求
        System.out.println("加载程序页面需求...");

        // 调用 calculatePageRequirements 方法，计算每个程序所需的页面数
        Map<String, Integer> pageRequirements = calculatePageRequirements(programs, pageSize);

        // 提示用户输入每个进程的最大页面数（内存容量限制）
        System.out.println("请输入每个进程的最大页面数:");
        Scanner scanner = new Scanner(System.in);
        int maxPages = scanner.nextInt(); // 接收用户输入的最大页面数

        // 提示用户选择页面调度算法
        System.out.println("请输入页面调度算法 (1. FIFO  2. LRU):");
        int choice = scanner.nextInt(); // 接收用户选择的算法类型（1 表示 FIFO，2 表示 LRU）

        // 创建一个页面管理器对象，根据页面大小和最大页面数初始化
        PageManager pageManager = new PageManager(pageSize, maxPages);

        // 输出调度过程开始信息
        System.out.println("页面调度过程:");
        int currentTime = 0; // 当前时间，用于模拟 LRU 页面最近使用时间

        // 遍历每个程序的页面需求
        for (Map.Entry<String, Integer> entry : pageRequirements.entrySet()) {
            String programName = entry.getKey(); // 获取程序名
            int pages = entry.getValue(); // 获取程序需要的页面数
            System.out.printf("程序 %s 需要 %d 页\n", programName, pages); // 输出程序的页面需求
            // 模拟加载程序所需的页面
            for (int page = 0; page < pages; page++) {
                if (choice == 1) {
                    // 如果用户选择 FIFO 调度算法
                    pageManager.fifoReplace(page); // 调用 FIFO 替换页面方法
                } else {
                    // 如果用户选择 LRU 调度算法
                    pageManager.lruReplace(page, currentTime); // 调用 LRU 替换页面方法
                }
                currentTime++; // 模拟时间的推移（用于 LRU 算法记录页面最近使用时间）
            }
        }
        // 输出页面置换日志
        System.out.println("\n页面置换日志:");
        for (String logEntry : pageManager.getLog()) {
            System.out.println(logEntry); // 打印每条页面置换日志
        }
        // 输出分页调度总结报告（包括命中率、页面置换次数等信息）
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

    public static void simulateCPU(Map<String, Integer> runTimes) {
        try (BufferedReader reader = new BufferedReader(new FileReader("run.txt"))) {
            String line; // 用于存储每行内容
            String currentProgram = null; // 当前正在读取的程序名
            Map<Integer, String> cpuLog = new TreeMap<>(); // 使用 TreeMap 按时间顺序存储 CPU 操作日志
            // 按行读取文件内容
            while ((line = reader.readLine()) != null) {
                line = line.trim(); // 去除首尾空白字符
                if (line.startsWith("程序名")) {
                    // 如果行以 "程序名" 开头，表示程序名的定义
                    currentProgram = line.split("\\s+")[1]; // 提取程序名并赋值给 currentProgram
                } else if (!line.isEmpty() && currentProgram != null) {
                    // 如果当前行非空，且已读取到程序名
                    String[] parts = line.split("\\s+"); // 按空白字符分割行内容
                    try {
                        int time = Integer.parseInt(parts[0]); // 第一个部分为时间点
                        String operation = parts[1]; // 第二个部分为操作描述
                        // 将时间点与操作描述加入日志 Map 中
                        cpuLog.put(time, "程序 " + currentProgram + ": " + operation);
                    } catch (NumberFormatException e) {
                        // 捕获时间解析错误
                        System.err.println("时间解析失败: " + line);
                    }
                }
            }
            // 输出动态模拟过程开始提示
            System.out.println("动态模拟 CPU 占用情况...");
            // 遍历日志记录，根据时间顺序逐条输出日志
            for (Map.Entry<Integer, String> entry : cpuLog.entrySet()) {
                Thread.sleep(1); // 每次延迟 1 毫秒，模拟时间流逝
                System.out.printf("时间: %dms | %s\n", entry.getKey(), entry.getValue()); // 输出当前时间和对应操作
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