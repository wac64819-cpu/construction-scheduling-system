package construction.entity;

public class ProjectSettings {
    // === 1. 成本与资源限制 (Cost & Resource Constraints) ===

    // 每日间接成本 (管理费、水电等)
    public double indirectCostPerDay = 1000.0;

    // 闲置/进出场惩罚成本 (每工种每闲置一天)
    public double idleCostPerDay = 200.0;

    // 工人数量倍率限制 (Max Outsourcing Ratio)
    // 例如 0.5 表示允许额外雇佣 50% 的人力。如果 Trade Capacity=10，则最大可用 15。
    public double maxOutsourcingRatio = 0.5;

    // 超额成本倍率 (Overtime/Outsourcing Cost Multiplier)
    // 例如 1.5 表示外包单价是正常单价的 1.5 倍
    public double outsourcingCostMultiplier = 1.5;

    // === 2. 遗传算法设置 (Genetic Algorithm Settings) ===

    // 最大评估次数 (迭代次数 * 种群大小)
    public int maxEvaluations = 1000;

    // 种群大小
    public int populationSize = 100;

    // 变异概率 (通常是 1.0 / 变量数，这里可以设个修正系数)
    public double mutationProbability = 0.01;

    // 交叉概率
    public double crossoverProbability = 0.9;

    // 无参构造
    public ProjectSettings() {}
}
