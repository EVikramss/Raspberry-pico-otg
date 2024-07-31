/* Modified Version of libaums file to support FAT 16 */

package me.jahnen.libaums.core.fs.fat16

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * This class represents the FAT32 boot sector which is always located at the
 * beginning of every FAT32 file system. It holds important information about
 * the file system such as the cluster size and the start cluster of the root
 * directory.
 *
 * @author mjahnen
 */
internal class Fat16BootSector private constructor() {

    /**
     * Returns the number of bytes in one single sector of a FAT32 file system.
     *
     * @return Number of bytes.
     */
    var bytesPerSector: Short = 0
        private set
    /**
     * Returns the number of sectors in one single cluster of a FAT32 file
     * system.
     *
     * @return Number of bytes.
     */
    var sectorsPerCluster: Short = 0
        private set
    /**
     * Returns the number of reserved sectors at the beginning of the FAT32 file
     * system. This includes one sector for the boot sector.
     *
     * @return Number of sectors.
     */
    var reservedSectors: Short = 0
        private set
    /**
     * Returns the number of the FATs in the FAT32 file system. This is mostly
     * 2.
     *
     * @return Number of FATs.
     */
    var fatCount: Byte = 0
        private set
    /**
     * Returns the total number of sectors in the file system.
     *
     * @return Total number of sectors.
     */
    var totalNumberOfSectors: Long = 0
        private set
    /**
     * Returns the total number of sectors in one file allocation table. The
     * FATs have a fixed size.
     *
     * @return Number of sectors in one FAT.
     */
    var sectorsPerFat: Long = 0
        private set
    /**
     * Returns the start cluster of the root directory in the FAT32 file system.
     *
     * @return Root directory start cluster.
     */
    var rootDirStartCluster: Long = 0
        private set
    /**
     * Returns the start sector of the file system info structure.
     *
     * @return FSInfo Structure start sector.
     */
    var fsInfoStartSector: Short = 0
        private set
    /**
     * Returns if the different FATs in the file system are mirrored, ie. all of
     * them are holding the same data. This is used for backup purposes.
     *
     * @return True if the FAT is mirrored.
     * @see .getValidFat
     * @see .getFatCount
     */
    var isFatMirrored: Boolean = false
        private set
    /**
     * Returns the valid FATs which shall be used if the FATs are not mirrored.
     *
     * @return Number of the valid FAT.
     * @see .isFatMirrored
     * @see .getFatCount
     */
    var validFat: Byte = 0
        private set
    /**
     * This returns the volume label stored in the boot sector. This is mostly
     * not used and you should instead use [FatDirectory.volumeLabel]
     * of the root directory.
     *
     * @return The volume label.
     */
    var volumeLabel: String? = null
        private set

    /**
     * Get the max root entries possible
     */
    var possibleRootEntries: Short = 0
        private set

    /**
     * Returns the amount in bytes in one cluster.
     *
     * @return Amount of bytes.
     */
    val bytesPerCluster: Int
        get() = sectorsPerCluster * bytesPerSector

    /**
     * Returns the offset in bytes from the beginning of the file system of the
     * data area. The data area is the area where the contents of directories
     * and files are saved. Present after root directory where each directory entry
     * is 32 bytes
     *
     * @return Offset in bytes.
     */
    val dataAreaOffset: Long
        get() = rootDirOffset + rootDirSize

    /*
    * Get root directory size
     */
    val rootDirSize: Int
        get() = (possibleRootEntries.toInt() * FatDirectoryEntry.SIZE).toInt()

    /*
    * Get root directory offset in bytes
     */
    val rootDirOffset: Long
        get() = getFatOffset(0) + (fatCount.toLong() * sectorsPerFat * bytesPerSector.toLong())

    /**
     * Returns the FAT offset in bytes from the beginning of the file system for
     * the given FAT number.
     *
     * @param fatNumber
     * The number of the FAT.
     * @return Offset in bytes.
     * @see .isFatMirrored
     * @see .getFatCount
     * @see .getValidFat
     */
    fun getFatOffset(fatNumber: Int): Long {
        // adding 1 to represent MBR
        return bytesPerSector * (1 + reservedSectors + (fatNumber * sectorsPerFat))
    }

    override fun toString(): String {
        return "Fat32BootSector{" +
                "bytesPerSector=" + bytesPerSector +
                ", sectorsPerCluster=" + sectorsPerCluster +
                ", reservedSectors=" + reservedSectors +
                ", fatCount=" + fatCount +
                ", totalNumberOfSectors=" + totalNumberOfSectors +
                ", sectorsPerFat=" + sectorsPerFat +
                ", rootDirStartCluster=" + rootDirStartCluster +
                ", fsInfoStartSector=" + fsInfoStartSector +
                ", fatMirrored=" + isFatMirrored +
                ", validFat=" + validFat +
                ", volumeLabel='" + volumeLabel + '\''.toString() +
                '}'.toString()
    }

    companion object {
        private const val BYTES_PER_SECTOR_OFF = 11
        private const val SECTORS_PER_CLUSTER_OFF = 13
        private const val RESERVED_COUNT_OFF = 14
        private const val FAT_COUNT_OFF = 16
        private const val ROOT_DIR_ENTRIES_OFF = 17
        private const val TOTAL_SECTORS_OFF = 32
        private const val SECTORS_PER_FAT_OFF = 22
        private const val FLAGS_OFF = 40
        private const val ROOT_DIR_CLUSTER_OFF = 44
        private const val FS_INFO_SECTOR_OFF = 48
        private const val VOLUME_LABEL_OFF = 48

        /**
         * Reads a FAT32 boot sector from the given buffer. The buffer has to be 512
         * (the size of a boot sector) bytes.
         *
         * @param buffer
         * The data where the boot sector is located.
         * @return A newly created boot sector.
         */
        @JvmStatic
        fun read(buffer: ByteBuffer): Fat16BootSector {
            val result = Fat16BootSector()
            buffer.order(ByteOrder.LITTLE_ENDIAN)
            result.bytesPerSector = buffer.getShort(BYTES_PER_SECTOR_OFF)
            result.sectorsPerCluster = (buffer.get(SECTORS_PER_CLUSTER_OFF).toInt() and 0xf).toShort()
            result.reservedSectors = buffer.getShort(RESERVED_COUNT_OFF)
            result.fatCount = buffer.get(FAT_COUNT_OFF)
            result.totalNumberOfSectors = buffer.getInt(TOTAL_SECTORS_OFF).toLong() and 0xffffL
            result.sectorsPerFat = buffer.getInt(SECTORS_PER_FAT_OFF).toLong() and 0xffffL
            result.rootDirStartCluster = 2.toLong() //(result.fatCount * result.sectorsPerFat) + 2
            result.possibleRootEntries = buffer.getShort(ROOT_DIR_ENTRIES_OFF)
            result.fsInfoStartSector = buffer.getShort(FS_INFO_SECTOR_OFF)
            val flag = buffer.getShort(FLAGS_OFF)
            result.isFatMirrored = flag.toInt() and 0x80 == 0
            result.validFat = (flag.toInt() and 0x7).toByte()

            val builder = StringBuilder()

            for (i in 0..10) {
                val b = buffer.get(VOLUME_LABEL_OFF + i)
                if (b.toInt() == 0)
                    break
                builder.append(b.toChar())
            }

            result.volumeLabel = builder.toString()

            return result
        }
    }
}
