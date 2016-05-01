package edu.colorado.plv.fixr;

import java.util.Collection;
import java.util.Collections;

import org.junit.Test;

public class SlicingTestCF extends TestSlicing {
	static final String CLASS_NAME = "slice.TestControlFlow";
	static final String RES_CLASS_NAME = "slice.TestControlFlowRes";		
	
	@Override
	public String getTestClassName()
	{
		return CLASS_NAME;
	}
	
	@Override
	public String getResClassName()
	{
		return RES_CLASS_NAME;
	}
	
	@Override	
	public Collection<String> getPackages() {
		return Collections.singletonList("java.lang.Math");
	}

	@Test
	public void t1(){testSlice("testSequence01");}

	@Test
	public void t2(){testSlice("testSequence02");}
	
	@Test
	public void t3(){testSlice("testSequence03");}
	
	@Test
	public void t4(){testSlice("testConditional01");}

	@Test
	public void t5(){testSlice("testConditional02");}
	
	@Test
	public void t6(){testSlice("testConditional03");}
	
	@Test
	public void t7(){testSlice("testLoop01");}
	@Test
	public void t8(){testSlice("testLoop02");}
	@Test
	public void t9(){testSlice("testLoop03");}
}