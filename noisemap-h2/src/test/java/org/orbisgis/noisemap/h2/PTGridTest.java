package org.orbisgis.noisemap.h2;

import org.h2gis.h2spatial.CreateSpatialExtension;
import org.h2gis.h2spatial.ut.SpatialH2UT;
import org.h2gis.utilities.SFSUtilities;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author Nicolas Fortin
 */
public class PTGridTest {
    private static Connection connection;
    private Statement st;

    @BeforeClass
    public static void tearUpClass() throws Exception {
        connection = SFSUtilities.wrapConnection(SpatialH2UT.createSpatialDataBase(PTGridTest.class.getSimpleName(), false, "MV_STORE=FALSE"));
        org.h2gis.h2spatialext.CreateSpatialExtension.initSpatialExtension(connection);
        CreateSpatialExtension.registerFunction(connection.createStatement(), new BR_PtGrid3D(), "");
        CreateSpatialExtension.registerFunction(connection.createStatement(), new BR_PtGrid(), "");
        CreateSpatialExtension.registerFunction(connection.createStatement(), new BR_SpectrumRepartition(), "");
        CreateSpatialExtension.registerFunction(connection.createStatement(), new BR_EvalSource(), "");
        CreateSpatialExtension.registerFunction(connection.createStatement(), new BR_SpectrumRepartition(), "");
    }

    @AfterClass
    public static void tearDownClass() throws SQLException {
        connection.close();
    }

    @Before
    public void setUp() throws Exception {
        st = connection.createStatement();
    }

    @After
    public void tearDown() throws Exception {
        st.close();
    }

    @Test
    public void testFreeField() throws SQLException {
        // Create empty buildings table
        st.execute("DROP TABLE IF EXISTS BUILDINGS");
        st.execute("CREATE TABLE BUILDINGS(the_geom POLYGON)");
        // Create a single sound source
        st.execute("DROP TABLE IF EXISTS roads_src_global");
        st.execute("CREATE TEMPORARY TABLE roads_src_global(the_geom POINT, db_m double)");
        st.execute("INSERT INTO roads_src_global VALUES ('POINT(0 0)'::geometry, 85)");
        // INSERT 2 points to set the computation area
        st.execute("INSERT INTO roads_src_global VALUES ('POINT(-20 -20)'::geometry, 0)");
        st.execute("INSERT INTO roads_src_global VALUES ('POINT(20 20)'::geometry, 0)");
        // Compute spectrum repartition
        st.execute("drop table if exists roads_src;\n" +
                "CREATE TABLE roads_src AS SELECT the_geom,\n" +
                "BR_SpectrumRepartition(100,1,db_m) as db_m100,\n" +
                "BR_SpectrumRepartition(125,1,db_m) as db_m125,\n" +
                "BR_SpectrumRepartition(160,1,db_m) as db_m160,\n" +
                "BR_SpectrumRepartition(200,1,db_m) as db_m200,\n" +
                "BR_SpectrumRepartition(250,1,db_m) as db_m250,\n" +
                "BR_SpectrumRepartition(315,1,db_m) as db_m315,\n" +
                "BR_SpectrumRepartition(400,1,db_m) as db_m400,\n" +
                "BR_SpectrumRepartition(500,1,db_m) as db_m500,\n" +
                "BR_SpectrumRepartition(630,1,db_m) as db_m630,\n" +
                "BR_SpectrumRepartition(800,1,db_m) as db_m800,\n" +
                "BR_SpectrumRepartition(1000,1,db_m) as db_m1000,\n" +
                "BR_SpectrumRepartition(1250,1,db_m) as db_m1250,\n" +
                "BR_SpectrumRepartition(1600,1,db_m) as db_m1600,\n" +
                "BR_SpectrumRepartition(2000,1,db_m) as db_m2000,\n" +
                "BR_SpectrumRepartition(2500,1,db_m) as db_m2500,\n" +
                "BR_SpectrumRepartition(3150,1,db_m) as db_m3150,\n" +
                "BR_SpectrumRepartition(4000,1,db_m) as db_m4000,\n" +
                "BR_SpectrumRepartition(5000,1,db_m) as db_m5000 from roads_src_global;");
        // Create receivers points
        st.execute("DROP TABLE IF EXISTS RECEIVERS");
        st.execute("CREATE TABLE RECEIVERS(ID SERIAL, THE_GEOM POINT)");
        st.execute("INSERT INTO RECEIVERS(THE_GEOM) VALUES ('POINT(5 0)')");
        st.execute("INSERT INTO RECEIVERS(THE_GEOM) VALUES ('POINT(10 0)')");
        st.execute("INSERT INTO RECEIVERS(THE_GEOM) VALUES ('POINT(15 0)')");
        // Compute noise map
        st.execute("DROP TABLE IF EXISTS TEST");
        ResultSet rs = st.executeQuery("SELECT * FROM BR_PTGRID('buildings', 'roads_src','receivers', 'DB_M','', 50,50, 2,2,0.2)");
        try {
            assertTrue(rs.next());
            assertEquals(1l, rs.getLong("GID"));
            assertEquals(0, rs.getInt("CELL_ID"));
            assertEquals(59.89, 10*Math.log10(rs.getDouble("W")), 0.01);
            assertTrue(rs.next());
            assertEquals(2l, rs.getLong("GID"));
            assertEquals(0, rs.getInt("CELL_ID"));
            assertEquals(53.84, 10*Math.log10(rs.getDouble("W")), 0.01);
            assertTrue(rs.next());
            assertEquals(3l, rs.getLong("GID"));
            assertEquals(0, rs.getInt("CELL_ID"));
            assertEquals(50.3, 10*Math.log10(rs.getDouble("W")), 0.01);
            assertFalse(rs.next());
        } finally {
            rs.close();
        }
    }


    @Test
    public void testDem() throws SQLException {
        st.execute("RUNSCRIPT FROM '"+PTGridTest.class.getResource("dem.sql").getFile()+"'");
        st.execute("drop table if exists pt_lvl");
        // Create receivers points
        st.execute("DROP TABLE IF EXISTS RECEIVERS");
        st.execute("CREATE TABLE RECEIVERS(ID SERIAL, THE_GEOM POINT)");
        st.execute("INSERT INTO RECEIVERS(THE_GEOM) VALUES ('POINT (-250.56607923708552 106.76760851263573 1.6)')");
        st.execute("INSERT INTO RECEIVERS(THE_GEOM) VALUES ('POINT (-250.56607923708552 106.76760851263573 25)')");
        ResultSet rs = st.executeQuery("select * from BR_PTGRID3D('BUILDINGS', 'HEIGHT', 'SOUND_SOURCE','RECEIVERS', 'DB_M', ''," +
                "'DEM' ,1000,500, 2, 1, 0.2)");
        //  W must be equal to 1
        try {
            assertTrue(rs.next());
            // First receiver is hidden by the hill
            assertEquals(1, rs.getDouble("W"), 0.01);
            // Second receiver is higher than the hill
            assertTrue(rs.next());
            assertEquals(52.5, 10 * Math.log10(rs.getDouble("W")), 0.1);
            assertFalse(rs.next());
        } finally {
            rs.close();
        }
    }

    @Test
    public void testGroundReflectionPropagation() throws SQLException {
        st.execute("DROP TABLE IF EXISTS LANDCOVER2000");
        st.execute("CALL SHPREAD('"+TriGridTest.class.getResource("landcover2000.shp").getFile()+"', 'LANDCOVER2000')");
        st.execute("RUNSCRIPT FROM '"+TriGridTest.class.getResource("ground-effect.sql").getFile()+"'");
        ResultSet rs = st.executeQuery("SELECT * FROM PT_LVL");
        assertTrue(rs.next());
        assertEquals(1, rs.getInt("GID"));
        assertEquals(51.98,10*Math.log10(rs.getDouble("W")), 0.1);
        assertTrue(rs.next());
        assertEquals(2, rs.getInt("GID"));
        assertEquals(50.28,10*Math.log10(rs.getDouble("W")), 0.1);
        assertFalse(rs.next());
    }
}
