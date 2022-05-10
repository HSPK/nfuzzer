package nfuzzer;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.util.Properties;

// 用于共享内存文件的操作的类和方法
public class Mem {
	
	public static final int SIZE = 131076;

	int fLen = SIZE;
	int fSize = 0;
	String shareFileName;
	String sharePath;
	MappedByteBuffer mapBuf = null;
	FileChannel fc = null;
	FileLock fl = null;
	Properties p = null;
	RandomAccessFile RAFile = null;

	byte[] mem = new byte[65536];

	/**
	 * 创建共享内存 (shared memory)
	 * 
	 * @param sp shm 文件路径
	 * @param sf shm 文件名
	 */
	private Mem(String sp, String sf) {
		if (sp.length() != 0) {
			File folder = new File(sp);
			if (!folder.exists()) {
				folder.mkdirs();
			}
		}

		this.shareFileName = sf;
		this.sharePath = sp + File.separator;

		try {
			// 获取一个随机存取文件对象
			RAFile = new RandomAccessFile(this.sharePath + this.shareFileName + ".sm", "rw");
			// 获取文件通道
			fc = RAFile.getChannel();
			// 获取文件大小
			fSize = (int) fc.size();
			if (fSize < fLen) {
				byte[] bb = new byte[fLen - fSize];
				// 创建字节缓冲区
				ByteBuffer bf = ByteBuffer.wrap(bb);
				bf.clear();
				// 设置文件通道的文件位置
				fc.position(fSize);
				// 将缓冲区内的字节写入通道中
				fc.write(bf);
				fc.force(false);
				fSize = fLen;
			}
			// 将此通道的文件区域映射到内存
			mapBuf = fc.map(FileChannel.MapMode.READ_WRITE, 0, fSize);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * @param ps    锁定区域的起始位置
	 * @param buff  要写入的数据
	 */
	public void write(int ps, byte[] buff) {

		// 定义锁定区域的标志
		FileLock fl = null;
		try {
			// 获取文件对应区域的锁
			fl = fc.lock(ps, 1, false);
			if (fl != null) {
				mapBuf.position(ps);
				ByteBuffer bf1 = ByteBuffer.wrap(buff);
				mapBuf.put(bf1);
				// 释放锁
				fl.release();
			}
		} catch (Exception e) {
			if (fl != null) {
				try {
					fl.release();
				} catch (IOException e1) {
					System.out.println(e1.toString());
				}
			}
		}
	}

	/**
	 * 清空65536字节的数据 (边缘覆盖或分支覆盖数据)
	 */
	public synchronized void write_all(int ps) {
        
        FileLock fl = null;
        try {
            
            fl = fc.lock(ps, 65536, false);
            if (fl != null) {
 
                mapBuf.position(ps);
                ByteBuffer bf1 = ByteBuffer.wrap(mem);
                mapBuf.put(bf1);
                
                fl.release();
            }
        } catch (Exception e) {
            if (fl != null) {
                try {
                    fl.release();
                } catch (IOException e1) {
                    System.out.println(e1.toString());
                }
            }
        }
    }

	/**
	 * 将 Integer 型数据转换成两个字节，写入对应位置
	 * 代表边缘覆盖所需的上一个位置或分支覆盖所需的总分支数
	 */
	public void write_TwoBytes(int ps, int number) {

		FileLock fl = null;
		try {
			fl = fc.lock(ps, 2, false);
			if (fl != null) {

				// 变量类型转换
				byte[] buff = new byte[2];
				buff[1] = (byte) (number & 0xff);
        		buff[0] = (byte) (number >> 8 & 0xff);

				mapBuf.position(ps);
				ByteBuffer bf1 = ByteBuffer.wrap(buff);
				mapBuf.put(bf1);
				fl.release();
			}
		} catch (Exception e) {
			if (fl != null) {
				try {
					fl.release();
				} catch (IOException e1) {
					System.out.println(e1.toString());
				}
			}
		}
	}

	/**
	 * @param buff 读取的数据
	 */
	public void read(int ps, byte[] buff) {

		FileLock fl = null;
		try{
			fl = fc.lock(ps, 1, false);
			if(fl != null){
				mapBuf.position(ps);
				int len = 1;
				if(mapBuf.remaining() < len){
					len = mapBuf.remaining();
				}

				if(len > 0){
					mapBuf.get(buff, 0, len);
				}
				fl.release();
			}
		} catch (Exception e) {
			if (fl != null) {
				try {
					fl.release();
				} catch (IOException e1) {
					System.out.println(e1.toString());
				}
			}
		}
	}

	/**
	 * 读取两个字节的数据，转换成 Integer 型返回
	 * 与 write_TwoBytes() 对应
	 */
	public int read_TwoBytes(int ps) {
		
		FileLock fl = null;
		try{
			fl = fc.lock(ps, 2, false);
			if(fl != null){
				mapBuf.position(ps);

				byte[] buff = new byte[2];
				mapBuf.get(buff, 0, 2);
				fl.release();

				int number = 0;
				number += (buff[0] & 0xff) << 8;
				number += (buff[1] & 0xff);

				return number;
			}
		} catch (Exception e) {
			if (fl != null) {
				try {
					fl.release();
				} catch (IOException e1) {
					System.out.println(e1.toString());
				}
			}
			return 0;
		}
		return 0;
	}

	public byte[] read_all(int ps) {
		
		byte[] zero = new byte[65536];

		FileLock fl = null;
		try{
			fl = fc.lock(ps, 65536, false);
			if(fl != null){
				mapBuf.position(ps);

				byte[] buff = new byte[65536];
				mapBuf.get(buff, 0, 65536);
				fl.release();

				return buff;
			}
		} catch (Exception e) {
			if (fl != null) {
				try {
					fl.release();
				} catch (IOException e1) {
					System.out.println(e1.toString());
				}
			}
			return zero;
		}
		return zero;
	}

	/**
	 * 关闭共享内存
	 */
	public void closeSMFile(){
		if (fc != null) {
            try {
                fc.close();
            } catch (IOException e) {
                System.out.println(e.toString());
            }
            fc = null;
        }

        if (RAFile != null) {
            try {
                RAFile.close();
            } catch (IOException e) {
                System.out.println(e.toString());
            }
            RAFile = null;
        }
        mapBuf = null;
	}

	// 计算边缘覆盖和分支覆盖，在程序被插桩后调用
	public static synchronized void coverage(int id, String sp){
		sm = Mem.getInstance(sp);

		// 边缘覆盖
		int last_id = sm.read_TwoBytes(65536);
		int write_area = id ^ last_id;
		sm.write_TwoBytes(65536, id >> 1);
		byte[] num = new byte[1];
		// sm.read(write_area, num);
		num[0] = 1;
		sm.write(write_area, num);

		// 分支覆盖
		sm.write(65538 + id, num);
		//sm.closeSMFile();
	}

	// 单例
	private static Mem sm;

	public static Mem getInstance(String sp) {
		if (null == sm) {
			sm = new Mem(sp, "bitmap");
		}
		return sm;
	}
}
