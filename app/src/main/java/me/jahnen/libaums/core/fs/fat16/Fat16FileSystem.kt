/* Modified Version of libaums file to support FAT 16 */

package me.jahnen.libaums.core.fs.fat16

import android.util.Log
import me.jahnen.libaums.core.driver.BlockDeviceDriver
import me.jahnen.libaums.core.fs.FileSystem
import me.jahnen.libaums.core.fs.UsbFile
import me.jahnen.libaums.core.partition.PartitionTypes
import java.io.IOException
import java.nio.ByteBuffer
import java.util.*

/**
 * This class represents the FAT16 file system and is responsible for setting
 * the FAT16 file system up and extracting the volume label and the root
 * directory.
 *
 * @author mjahnen
 */
class Fat16FileSystem
/**
 * This method constructs a FAT16 file system for the given block device.
 * There are no further checks that the block device actually represents a
 * valid FAT16 file system. That means it must be ensured that the device
 * actually holds a FAT16 file system in advance!
 *
 * @param blockDevice
 * The block device the FAT16 file system is located.
 * @param first512Bytes
 * First 512 bytes read from block device.
 * @throws IOException
 * If reading from the device fails.
 */
@Throws(IOException::class)
private constructor(blockDevice: BlockDeviceDriver, first512Bytes: ByteBuffer) : FileSystem {

    private val bootSector: Fat16BootSector = Fat16BootSector.read(first512Bytes)
    private val fat: FAT
    override val rootDirectory: FatDirectory
    /**
     * Caches UsbFile instances returned by list files method. If we do not do
     * that we will get a new instance when searching for the same file.
     * Depending on what you do with the two different instances they can get out
     * of sync and only the one which is written latest will actually be persisted on
     * disk. This is especially problematic if you create files on different directory
     * instances.. See also issue 215.
     */
    internal val fileCache = WeakHashMap<String, UsbFile>()

    override val volumeLabel: String
        get() {
            return rootDirectory.volumeLabel?.let { it }.orEmpty()
        }

    override val capacity: Long
        get() = bootSector.totalNumberOfSectors * bootSector.bytesPerSector

    override val occupiedSpace: Long
        get() = 0.toLong() // TBD

    override val freeSpace: Long
        get() = capacity - occupiedSpace

    override val chunkSize: Int
        get() = bootSector.bytesPerCluster

    override val type: Int
        get() = PartitionTypes.FAT16

    init {
        fat = FAT(blockDevice, bootSector)
        rootDirectory = FatDirectory.readRoot(this, blockDevice, fat, bootSector)

        Log.d(TAG, bootSector.toString())
    }

    companion object {

        private val TAG = Fat16FileSystem::class.java.simpleName

        /**
         * This method constructs a FAT16 file system for the given block device.
         * There are no further checks if the block device actually represents a
         * valid FAT16 file system. That means it must be ensured that the device
         * actually holds a FAT16 file system in advance!
         *
         * @param blockDevice
         * The block device the FAT16 file system is located.
         * @throws IOException
         * If reading from the device fails.
         */
        @Throws(IOException::class)
        @JvmStatic
        fun read(blockDevice: BlockDeviceDriver): Fat16FileSystem? {

            val buffer = ByteBuffer.allocate(512)
            blockDevice.read(0, buffer)

            if (buffer.get(510).toInt() != 85 ||
                buffer.get(511).toInt() != -86) {
                return null
            } else {
                val buffer2 = ByteBuffer.allocate(512)
                blockDevice.read(512.toLong(), buffer2)
                if (buffer2.get(54).toChar() != 'F' ||
                    buffer2.get(55).toChar() != 'A' ||
                    buffer2.get(56).toChar() != 'T' ||
                    buffer2.get(57).toChar() != '1' ||
                    buffer2.get(58).toChar() != '6') {
                    return null
                } else {
                    //buffer.flip()
                    return Fat16FileSystem(blockDevice, buffer2)
                }
            }
        }
    }
}
