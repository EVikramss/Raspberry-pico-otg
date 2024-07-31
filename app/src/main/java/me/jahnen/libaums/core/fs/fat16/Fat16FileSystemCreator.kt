/* Modified Version of libaums file to support FAT 16 */

package me.jahnen.libaums.core.fs.fat16

import me.jahnen.libaums.core.driver.BlockDeviceDriver
import me.jahnen.libaums.core.fs.FileSystem
import me.jahnen.libaums.core.fs.FileSystemCreator
import me.jahnen.libaums.core.partition.PartitionTableEntry
import java.io.IOException

class Fat16FileSystemCreator : FileSystemCreator {

    @Throws(IOException::class)
    override fun read(entry: PartitionTableEntry, blockDevice: BlockDeviceDriver): FileSystem? {
        return Fat16FileSystem.read(blockDevice)
    }
}
