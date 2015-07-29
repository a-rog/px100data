package com.px100systems.util.test;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

/**
 * All test cases for debugging
 * 
* Copyright (c) 2015 Px100 Systems. All Rights Reserved.
  * @author Alex Rogachevsky
 */
public class MainUnitTest extends TestCase {
    public MainUnitTest(String testName) {
        super(testName);
    }

    public static Test suite() {
        return new TestSuite(MainUnitTest.class);
    }

    public void testSerialization() {
		assertTrue(true);
	}
}
