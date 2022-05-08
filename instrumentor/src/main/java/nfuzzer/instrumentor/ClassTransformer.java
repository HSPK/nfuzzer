package nfuzzer.instrumentor;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

// 调用 MethodTransformer
public class ClassTransformer extends ClassVisitor {

	private String sp;

	public ClassTransformer(ClassVisitor cv, String sp) {
		super(Opcodes.ASM5, cv);
		this.sp = sp;
	}

	@Override
	public MethodVisitor visitMethod(
		int access, 
		String name,
		String desc, 
		String signature, 
		String[] exceptions) {
		
		MethodVisitor mv;
		mv = cv.visitMethod(access, name, desc, signature, exceptions);

		if (mv != null) {
			try {
				recordLog.writeLog("visit method: " + name);
			} catch (Exception e) {
				e.printStackTrace();
			}
			mv = new MethodTransformer(mv, sp);
		}
		return mv;
	}
}
