package LDS.Person.util.GetSystemInfo;

import oshi.SystemInfo;
import oshi.hardware.GlobalMemory;
import oshi.hardware.HardwareAbstractionLayer;
import oshi.hardware.PowerSource;
import oshi.software.os.FileSystem;
import oshi.software.os.OSFileStore;
import oshi.software.os.OperatingSystem;

import java.io.Serializable;
import java.util.List;

/**
 * 系统数据收集器 - 同时作为数据模型
 * 负责收集并存储 OS、CPU、内存、磁盘、电池等系统信息
 */
public class GetSystemInfoDataCollector implements Serializable {

  // =============== 操作系统信息 ===============
  private String osName;

  // =============== 电池信息 ===============
  private String batteryName;
  private String deviceName;
  private int currentCapacity;
  private int maxCapacity;
  private double remainingCapacityPercent;
  private double voltage;
  private double amperage;
  private double powerRate;
  private String chargeStatus;
  private boolean powerOnLine;
  private String chemistry;
  private String manufacturer;

  // =============== 处理器信息 ===============
  private String processorName;
  private int physicalCoreCount;
  private int logicalCoreCount;
  private double currentFreq;
  private double maxFreq;

  // =============== CPU、内存、磁盘信息 ===============
  private double cpuUsage;
  private double memoryUsagePercent;
  private long totalMemory;
  private long usedMemory;
  private double disk0UsagePercent;
  private String disk0Name;

  // =============== 收集方法 ===============

  /**
   * 收集所有系统信息
   */
  public GetSystemInfoDataCollector collect() {
    SystemInfo si = new SystemInfo();
    HardwareAbstractionLayer hal = si.getHardware();
    OperatingSystem os = si.getOperatingSystem();

    // 操作系统信息
    setOsName(os.toString());

    // 电池信息
    collectBatteryInfo(hal);

    // 处理器信息
    collectProcessorInfo(hal);

    // 性能指标（CPU、内存、磁盘）
    collectPerformanceInfo(hal, os);

    return this;
  }

  /**
   * 收集电池信息
   */
  private void collectBatteryInfo(HardwareAbstractionLayer hal) {
    List<PowerSource> powerSources = hal.getPowerSources();

    if (!powerSources.isEmpty()) {
      PowerSource ps = powerSources.get(0);
      setBatteryName(ps.getName());
      setDeviceName(ps.getDeviceName());
      setCurrentCapacity(ps.getCurrentCapacity());
      setMaxCapacity(ps.getMaxCapacity());
      setRemainingCapacityPercent(ps.getRemainingCapacityPercent());
      setVoltage(ps.getVoltage());
      setAmperage(ps.getAmperage());
      setPowerRate(ps.getPowerUsageRate() / 1000);
      setChargeStatus(ps.isCharging() ? "充电中" : ps.isDischarging() ? "放电中" : "未知");
      setPowerOnLine(ps.isPowerOnLine());
      setChemistry(ps.getChemistry());
      setManufacturer(ps.getManufacturer());
    }
  }

  /**
   * 收集处理器信息
   */
  private void collectProcessorInfo(HardwareAbstractionLayer hal) {
    setProcessorName(hal.getProcessor().getProcessorIdentifier().getName());
    setPhysicalCoreCount(hal.getProcessor().getPhysicalProcessorCount());
    setLogicalCoreCount(hal.getProcessor().getLogicalProcessorCount());

    long[] currentFreq = hal.getProcessor().getCurrentFreq();
    if (currentFreq != null && currentFreq.length > 0) {
      setCurrentFreq(currentFreq[0] / 1_000_000_000.0);
    }

    long maxFreq = hal.getProcessor().getMaxFreq();
    if (maxFreq > 0) {
      setMaxFreq(maxFreq / 1_000_000_000.0);
    }
  }

  /**
   * 收集性能指标（CPU、内存、磁盘）
   */
  private void collectPerformanceInfo(HardwareAbstractionLayer hal, OperatingSystem os) {
    // CPU 使用率
    double cpuLoad = hal.getProcessor().getSystemCpuLoad(1000) * 100;
    setCpuUsage(cpuLoad >= 0 ? Math.min(100.0, cpuLoad) : 0);

    // 内存信息
    GlobalMemory memory = hal.getMemory();
    long totalMemory = memory.getTotal();
    long usedMemory = totalMemory - memory.getAvailable();
    double memoryPercent = (usedMemory * 100.0) / totalMemory;

    setTotalMemory(totalMemory);
    setUsedMemory(usedMemory);
    setMemoryUsagePercent(memoryPercent);

    // 磁盘信息（主磁盘）
    FileSystem fileSystem = os.getFileSystem();
    if (!fileSystem.getFileStores().isEmpty()) {
      OSFileStore store = fileSystem.getFileStores().get(0);
      long total = store.getTotalSpace();
      long usable = store.getUsableSpace();
      long used = total - usable;
      double diskPercent = total > 0 ? (used * 100.0) / total : 0;

      setDisk0Name(store.getName());
      setDisk0UsagePercent(diskPercent);
    }
  }

  // =============== Getters and Setters ===============

  public String getOsName() {
    return osName;
  }

  public void setOsName(String osName) {
    this.osName = osName;
  }

  public String getBatteryName() {
    return batteryName;
  }

  public void setBatteryName(String batteryName) {
    this.batteryName = batteryName;
  }

  public String getDeviceName() {
    return deviceName;
  }

  public void setDeviceName(String deviceName) {
    this.deviceName = deviceName;
  }

  public int getCurrentCapacity() {
    return currentCapacity;
  }

  public void setCurrentCapacity(int currentCapacity) {
    this.currentCapacity = currentCapacity;
  }

  public int getMaxCapacity() {
    return maxCapacity;
  }

  public void setMaxCapacity(int maxCapacity) {
    this.maxCapacity = maxCapacity;
  }

  public double getRemainingCapacityPercent() {
    return remainingCapacityPercent;
  }

  public void setRemainingCapacityPercent(double remainingCapacityPercent) {
    this.remainingCapacityPercent = remainingCapacityPercent;
  }

  public double getVoltage() {
    return voltage;
  }

  public void setVoltage(double voltage) {
    this.voltage = voltage;
  }

  public double getAmperage() {
    return amperage;
  }

  public void setAmperage(double amperage) {
    this.amperage = amperage;
  }

  public double getPowerRate() {
    return powerRate;
  }

  public void setPowerRate(double powerRate) {
    this.powerRate = powerRate;
  }

  public String getChargeStatus() {
    return chargeStatus;
  }

  public void setChargeStatus(String chargeStatus) {
    this.chargeStatus = chargeStatus;
  }

  public boolean isPowerOnLine() {
    return powerOnLine;
  }

  public void setPowerOnLine(boolean powerOnLine) {
    this.powerOnLine = powerOnLine;
  }

  public String getChemistry() {
    return chemistry;
  }

  public void setChemistry(String chemistry) {
    this.chemistry = chemistry;
  }

  public String getManufacturer() {
    return manufacturer;
  }

  public void setManufacturer(String manufacturer) {
    this.manufacturer = manufacturer;
  }

  public String getProcessorName() {
    return processorName;
  }

  public void setProcessorName(String processorName) {
    this.processorName = processorName;
  }

  public int getPhysicalCoreCount() {
    return physicalCoreCount;
  }

  public void setPhysicalCoreCount(int physicalCoreCount) {
    this.physicalCoreCount = physicalCoreCount;
  }

  public int getLogicalCoreCount() {
    return logicalCoreCount;
  }

  public void setLogicalCoreCount(int logicalCoreCount) {
    this.logicalCoreCount = logicalCoreCount;
  }

  public double getCurrentFreq() {
    return currentFreq;
  }

  public void setCurrentFreq(double currentFreq) {
    this.currentFreq = currentFreq;
  }

  public double getMaxFreq() {
    return maxFreq;
  }

  public void setMaxFreq(double maxFreq) {
    this.maxFreq = maxFreq;
  }

  public double getCpuUsage() {
    return cpuUsage;
  }

  public void setCpuUsage(double cpuUsage) {
    this.cpuUsage = cpuUsage;
  }

  public double getMemoryUsagePercent() {
    return memoryUsagePercent;
  }

  public void setMemoryUsagePercent(double memoryUsagePercent) {
    this.memoryUsagePercent = memoryUsagePercent;
  }

  public long getTotalMemory() {
    return totalMemory;
  }

  public void setTotalMemory(long totalMemory) {
    this.totalMemory = totalMemory;
  }

  public long getUsedMemory() {
    return usedMemory;
  }

  public void setUsedMemory(long usedMemory) {
    this.usedMemory = usedMemory;
  }

  public double getDisk0UsagePercent() {
    return disk0UsagePercent;
  }

  public void setDisk0UsagePercent(double disk0UsagePercent) {
    this.disk0UsagePercent = disk0UsagePercent;
  }

  public String getDisk0Name() {
    return disk0Name;
  }

  public void setDisk0Name(String disk0Name) {
    this.disk0Name = disk0Name;
  }

  @Override
  public String toString() {
    return "DataCollector{" +
        "osName='" + osName + '\'' +
        ", batteryName='" + batteryName + '\'' +
        ", remainingCapacityPercent=" + remainingCapacityPercent +
        ", powerRate=" + powerRate +
        ", processorName='" + processorName + '\'' +
        ", currentFreq=" + currentFreq +
        ", maxFreq=" + maxFreq +
        '}';
  }
}
