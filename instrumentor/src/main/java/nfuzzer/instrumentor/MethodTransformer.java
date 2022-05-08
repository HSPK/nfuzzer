package nfuzzer.instrumentor;

import org.objectweb.asm.MethodVisitor;
import static org.objectweb.asm.Opcodes.*;
import org.objectweb.asm.Label;
import nfuzzer.Mem;

import java.util.HashSet;
import java.util.Random;

//TODO 在这里修改，给不同分支分配不同权重
/**
 * 对每一个分支进行插桩，插入类似 AFL 的代码
 * 使用 ASM 进行插桩
 * 插桩点为每一个方法的开头和每一个分支的开头(if、else、switch)
 */
public class MethodTransformer extends MethodVisitor {
	
	private HashSet<Integer> ids;
	Random r;

	public static int mark = 0;
	public static Label lab = new Label();

	public static int lab_switch_length;
	public static Label[] lab_switch = new Label[100];

	private String sp;

	public MethodTransformer(MethodVisitor mv, String sp) {
		super(ASM5, mv);

		ids = new HashSet<>();
		r = new Random();

		this.sp = sp;
	}
	
	// 尽可能生成一个没有使用过的随机数
	private int getNewLocationId() {
		int id;
		int tries = 0;
		do {
			id = r.nextInt(65536);
			tries++;
		} while (tries <= 10 && ids.contains(id));
		ids.add(id);
		return id;
	}

	private void instrumentLocation() {

		// 分支总数 + 1
		int branchSum = Mem.getInstance(sp).read_TwoBytes(131074);
		branchSum++;
		Mem.getInstance(sp).write_TwoBytes(131074, branchSum);

		/**
		 * 插桩代码 : 生成一个随机数，调用函数
		 * 当程序运行到插桩点时，会计算边缘覆盖和分支覆盖
		 */
		Integer id = getNewLocationId();
		mv.visitLdcInsn(id);
		mv.visitLdcInsn(sp);
		mv.visitMethodInsn(INVOKESTATIC, "nfuzzer/Mem", "coverage", "(ILjava/lang/String;)V", false);
	}

	@Override
	public void visitCode() {
		super.visitCode();
		instrumentLocation();
	}

	@Override
	public void visitJumpInsn(int opcode, Label label) {
		
		if(opcode >= IFEQ && opcode <= IF_ACMPNE){
			super.visitJumpInsn(opcode, label);
			lab = label;
		}else if(opcode == GOTO){
			instrumentLocation();
			super.visitJumpInsn(opcode, label);
		}else{
			super.visitJumpInsn(opcode, label);
		}
	}

	@Override
	public void visitFrame(
		final int type,
		final int numLocal,
		final Object[] local,
		final int numStack,
		final Object[] stack){
			
		if(mark != 0){
			super.visitFrame(type, numLocal, local, numStack, stack);
			instrumentLocation();
			mark = 0;
		}else {
			super.visitFrame(type, numLocal, local, numStack, stack);
		}
	}

	@Override
	public void visitTableSwitchInsn(
		final int min,
		final int max,
		final Label dflt,
		final Label... labels) {
	    	
		lab_switch = labels;
	    lab_switch_length = labels.length;
	    super.visitTableSwitchInsn(min, max, dflt, labels);
	}
	    
	@Override
	public void visitLookupSwitchInsn(
		final Label dflt, 
		final int[] keys, 
		final Label[] labels) {

	    lab_switch = labels;
	    lab_switch_length = labels.length;
	    //System.out.println(labels.length);
	    super.visitLookupSwitchInsn(dflt, keys, labels);
	}
	    
	@Override
	public void visitLabel(final Label label) {

	    if (label == lab) {
			mark = 1;
		}

		for (int i = 0; i < lab_switch_length; i++) {
			if (label == lab_switch[i] ) {
		   		mark = 2;
		   		break;
	    	}	
		}	

		super.visitLabel(label);
	}
}
