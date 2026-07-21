	new tmpobjid;

    new carvid;

    sapdpatrul[0] = CreateVehicle(596,1570.2423, -1710.9011, 5.5755, 0.0000,53, 1,CAR_SPAWN); // lspd copcar
    CreateVehicle(596,1574.3743, -1710.9011, 5.5755); // lspd copcar
    CreateVehicle(596,1578.5063, -1710.9011, 5.5755); // lspd copcar
    CreateVehicle(596,1583.4403, -1710.9011, 5.5755); // lspd copcar
    CreateVehicle(596,1587.3743, -1710.9011, 5.5755); // lspd copcar
    CreateVehicle(596,1591.4403, -1710.9011, 5.5755); // lspd copcar
    CreateVehicle(596,1595.3743, -1710.9011, 5.5755); // lspd copcar
    CreateVehicle(427,1545.3458, -1659.0269, 5.8542); // lspd enfoncer
    CreateVehicle(427,1545.3458, -1655.0269, 5.8542); // lspd enfoncer
    CreateVehicle(427,1545.3458, -1651.2369, 5.8542); // lspd enfoncer
    CreateVehicle(596,1572.02441406,-1605.98730469,13.20281219); //Police Car (LSPD)
    CreateVehicle(596,1587.17089844,-1606.42187500,13.20281219); //Police Car (LSPD)
    CreateVehicle(596,1575.66821289,-1606.22583008,13.20281219); //Police Car (LSPD)
    CreateVehicle(596,1579.50097656,-1606.26953125,13.20281219); //Police Car (LSPD)
    CreateVehicle(596,1583.29980469,-1606.45605469,13.20281219); //Police Car (LSPD)
    carvid = CreateVehicle(560,1536.00012207,-1668.41052246,13.20281219);
    markcarid[0][0]=carvid;
    tmpobjid = CreateDynamicObject(19797,0.0,0.0,-1000.0,0.0,0.0,0.0,0,0,-1,300.0,300.0);
    AttachDynamicObjectToVehicle(tmpobjid, carvid, -0.226, 2.628, -0.210, 0.000, 0.000, 179.900);
    tmpobjid = CreateDynamicObject(19797,0.0,0.0,-1000.0,0.0,0.0,0.0,0,0,-1,300.0,300.0);
    AttachDynamicObjectToVehicle(tmpobjid, carvid, 0.243, 2.619, -0.210, 0.000, 0.000, 179.199);
    tmpobjid = CreateDynamicObject(19797,0.0,0.0,-1000.0,0.0,0.0,0.0,0,0,-1,300.0,300.0);
    AttachDynamicObjectToVehicle(tmpobjid, carvid, 1.071, 1.159, 0.140, 0.000, 0.000, 88.799);
    tmpobjid = CreateDynamicObject(19797,0.0,0.0,-1000.0,0.0,0.0,0.0,0,0,-1,300.0,300.0);
    AttachDynamicObjectToVehicle(tmpobjid, carvid, -1.075, 1.113, 0.150, 0.000, 0.000, -85.500);
    tmpobjid = CreateDynamicObject(19620,0.0,0.0,-1000.0,0.0,0.0,0.0,0,0,-1,300.0,300.0);
    AttachDynamicObjectToVehicle(tmpobjid, carvid, 0.000, 0.000, 0.810, 0.000, 0.000, 0.000);
    tmpobjid = CreateDynamicObject(19893,0.0,0.0,-1000.0,0.0,0.0,0.0,0,0,-1,300.0,300.0);
    AttachDynamicObjectToVehicle(tmpobjid, carvid, 0.000, 0.400, -0.150, 0.000, 0.000, 0.000);
    tmpobjid = CreateDynamicObject(19477, 0.000000, 0.000000, 0.000000, 0.000000, 0.000000, 0.000000);
    SetDynamicObjectMaterialText(tmpobjid, 0, "LSPD", 140, "Impact", 80, 0, -1, 0, 1);
    AttachDynamicObjectToVehicle(tmpobjid, carvid, -0.007103, 1.924879, 0.310746, -0.699963, -77.499931, -269.801422);
    tmpobjid = CreateDynamicObject(19477, 0.000000, 0.000000, 0.000000, 0.000000, 0.000000, 0.000000);
    SetDynamicObjectMaterialText(tmpobjid, 0, "LSPD", 140, "Impact", 80, 0, -1, 0, 1);
    AttachDynamicObjectToVehicle(tmpobjid, carvid, -1.021961, -1.717599, 0.238558, 0.000000, -13.300002, -178.700073);
    tmpobjid = CreateDynamicObject(19477, 0.000000, 0.000000, 0.000000, 0.000000, 0.000000, 0.000000);
    SetDynamicObjectMaterialText(tmpobjid, 0, "LSPD", 140, "Impact", 80, 0, -1, 0, 1);
    AttachDynamicObjectToVehicle(tmpobjid, carvid, 1.042553, -1.651380, 0.209145, 0.000000, -13.300002, -361.600158);
    carvid = CreateVehicle(560,1602.53906, -1691.99219, 5.696);
    markcarid[0][1]=carvid;
    tmpobjid = CreateDynamicObject(19797,0.0,0.0,-1000.0,0.0,0.0,0.0,0,0,-1,300.0,300.0);
    AttachDynamicObjectToVehicle(tmpobjid, carvid, -0.226, 2.628, -0.210, 0.000, 0.000, 179.900);
    tmpobjid = CreateDynamicObject(19797,0.0,0.0,-1000.0,0.0,0.0,0.0,0,0,-1,300.0,300.0);
    AttachDynamicObjectToVehicle(tmpobjid, carvid, 0.243, 2.619, -0.210, 0.000, 0.000, 179.199);
    tmpobjid = CreateDynamicObject(19797,0.0,0.0,-1000.0,0.0,0.0,0.0,0,0,-1,300.0,300.0);
    AttachDynamicObjectToVehicle(tmpobjid, carvid, 1.071, 1.159, 0.140, 0.000, 0.000, 88.799);
    tmpobjid = CreateDynamicObject(19797,0.0,0.0,-1000.0,0.0,0.0,0.0,0,0,-1,300.0,300.0);
    AttachDynamicObjectToVehicle(tmpobjid, carvid, -1.075, 1.113, 0.150, 0.000, 0.000, -85.500);
    tmpobjid = CreateDynamicObject(19620,0.0,0.0,-1000.0,0.0,0.0,0.0,0,0,-1,300.0,300.0);
    AttachDynamicObjectToVehicle(tmpobjid, carvid, 0.000, 0.000, 0.810, 0.000, 0.000, 0.000);
    tmpobjid = CreateDynamicObject(19893,0.0,0.0,-1000.0,0.0,0.0,0.0,0,0,-1,300.0,300.0);
    AttachDynamicObjectToVehicle(tmpobjid, carvid, 0.000, 0.400, -0.150, 0.000, 0.000, 0.000);
    tmpobjid = CreateDynamicObject(19477, 0.000000, 0.000000, 0.000000, 0.000000, 0.000000, 0.000000);
    SetDynamicObjectMaterialText(tmpobjid, 0, "LSPD", 140, "Impact", 80, 0, -1, 0, 1);
    AttachDynamicObjectToVehicle(tmpobjid, carvid, -0.007103, 1.924879, 0.310746, -0.699963, -77.499931, -269.801422);
    tmpobjid = CreateDynamicObject(19477, 0.000000, 0.000000, 0.000000, 0.000000, 0.000000, 0.000000);
    SetDynamicObjectMaterialText(tmpobjid, 0, "LSPD", 140, "Impact", 80, 0, -1, 0, 1);
    AttachDynamicObjectToVehicle(tmpobjid, carvid, -1.021961, -1.717599, 0.238558, 0.000000, -13.300002, -178.700073);
    tmpobjid = CreateDynamicObject(19477, 0.000000, 0.000000, 0.000000, 0.000000, 0.000000, 0.000000);
    SetDynamicObjectMaterialText(tmpobjid, 0, "LSPD", 140, "Impact", 80, 0, -1, 0, 1);
    AttachDynamicObjectToVehicle(tmpobjid, carvid, 1.042553, -1.651380, 0.209145, 0.000000, -13.300002, -361.600158);
    carvid = CreateVehicle(560,1602.53906, -1687.99219, 5.696);
    markcarid[0][2]=carvid;
    tmpobjid = CreateDynamicObject(19797,0.0,0.0,-1000.0,0.0,0.0,0.0,0,0,-1,300.0,300.0);
    AttachDynamicObjectToVehicle(tmpobjid, carvid, -0.226, 2.628, -0.210, 0.000, 0.000, 179.900);
    tmpobjid = CreateDynamicObject(19797,0.0,0.0,-1000.0,0.0,0.0,0.0,0,0,-1,300.0,300.0);
    AttachDynamicObjectToVehicle(tmpobjid, carvid, 0.243, 2.619, -0.210, 0.000, 0.000, 179.199);
    tmpobjid = CreateDynamicObject(19797,0.0,0.0,-1000.0,0.0,0.0,0.0,0,0,-1,300.0,300.0);
    AttachDynamicObjectToVehicle(tmpobjid, carvid, 1.071, 1.159, 0.140, 0.000, 0.000, 88.799);
    tmpobjid = CreateDynamicObject(19797,0.0,0.0,-1000.0,0.0,0.0,0.0,0,0,-1,300.0,300.0);
    AttachDynamicObjectToVehicle(tmpobjid, carvid, -1.075, 1.113, 0.150, 0.000, 0.000, -85.500);
    tmpobjid = CreateDynamicObject(19620,0.0,0.0,-1000.0,0.0,0.0,0.0,0,0,-1,300.0,300.0);
    AttachDynamicObjectToVehicle(tmpobjid, carvid, 0.000, 0.000, 0.810, 0.000, 0.000, 0.000);
    tmpobjid = CreateDynamicObject(19893,0.0,0.0,-1000.0,0.0,0.0,0.0,0,0,-1,300.0,300.0);
    AttachDynamicObjectToVehicle(tmpobjid, carvid, 0.000, 0.400, -0.150, 0.000, 0.000, 0.000);
    tmpobjid = CreateDynamicObject(19477, 0.000000, 0.000000, 0.000000, 0.000000, 0.000000, 0.000000);
    SetDynamicObjectMaterialText(tmpobjid, 0, "LSPD", 140, "Impact", 80, 0, -1, 0, 1);
    AttachDynamicObjectToVehicle(tmpobjid, carvid, -0.007103, 1.924879, 0.310746, -0.699963, -77.499931, -269.801422);
    tmpobjid = CreateDynamicObject(19477, 0.000000, 0.000000, 0.000000, 0.000000, 0.000000, 0.000000);
    SetDynamicObjectMaterialText(tmpobjid, 0, "LSPD", 140, "Impact", 80, 0, -1, 0, 1);
    AttachDynamicObjectToVehicle(tmpobjid, carvid, -1.021961, -1.717599, 0.238558, 0.000000, -13.300002, -178.700073);
    tmpobjid = CreateDynamicObject(19477, 0.000000, 0.000000, 0.000000, 0.000000, 0.000000, 0.000000);
    SetDynamicObjectMaterialText(tmpobjid, 0, "LSPD", 140, "Impact", 80, 0, -1, 0, 1);
    AttachDynamicObjectToVehicle(tmpobjid, carvid, 1.042553, -1.651380, 0.209145, 0.000000, -13.300002, -361.600158);
    boost_lspdcar = CreateVehicle(560,1602.53992, -1684.02002, 5.696);
    markcarid[0][3]=boost_lspdcar;
    tmpobjid = CreateDynamicObject(19797,0.0,0.0,-1000.0,0.0,0.0,0.0,0,0,-1,300.0,300.0);
    AttachDynamicObjectToVehicle(tmpobjid, boost_lspdcar, -0.226, 2.628, -0.210, 0.000, 0.000, 179.900);
    tmpobjid = CreateDynamicObject(19797,0.0,0.0,-1000.0,0.0,0.0,0.0,0,0,-1,300.0,300.0);
    AttachDynamicObjectToVehicle(tmpobjid, boost_lspdcar, 0.243, 2.619, -0.210, 0.000, 0.000, 179.199);
    tmpobjid = CreateDynamicObject(19797,0.0,0.0,-1000.0,0.0,0.0,0.0,0,0,-1,300.0,300.0);
    AttachDynamicObjectToVehicle(tmpobjid, boost_lspdcar, 1.071, 1.159, 0.140, 0.000, 0.000, 88.799);
    tmpobjid = CreateDynamicObject(19797,0.0,0.0,-1000.0,0.0,0.0,0.0,0,0,-1,300.0,300.0);
    AttachDynamicObjectToVehicle(tmpobjid, boost_lspdcar, -1.075, 1.113, 0.150, 0.000, 0.000, -85.500);
    tmpobjid = CreateDynamicObject(19620,0.0,0.0,-1000.0,0.0,0.0,0.0,0,0,-1,300.0,300.0);
    AttachDynamicObjectToVehicle(tmpobjid, boost_lspdcar, 0.000, 0.000, 0.810, 0.000, 0.000, 0.000);
    tmpobjid = CreateDynamicObject(19893,0.0,0.0,-1000.0,0.0,0.0,0.0,0,0,-1,300.0,300.0);
    AttachDynamicObjectToVehicle(tmpobjid, boost_lspdcar, 0.000, 0.400, -0.150, 0.000, 0.000, 0.000);
    tmpobjid = CreateDynamicObject(19477, 0.000000, 0.000000, 0.000000, 0.000000, 0.000000, 0.000000);
    SetDynamicObjectMaterialText(tmpobjid, 0, "LSPD", 140, "Impact", 80, 0, -1, 0, 1);
    AttachDynamicObjectToVehicle(tmpobjid, boost_lspdcar, -0.007103, 1.924879, 0.310746, -0.699963, -77.499931, -269.801422);
    tmpobjid = CreateDynamicObject(19477, 0.000000, 0.000000, 0.000000, 0.000000, 0.000000, 0.000000);
    SetDynamicObjectMaterialText(tmpobjid, 0, "LSPD", 140, "Impact", 80, 0, -1, 0, 1);
    AttachDynamicObjectToVehicle(tmpobjid, boost_lspdcar, -1.021961, -1.717599, 0.238558, 0.000000, -13.300002, -178.700073);
    tmpobjid = CreateDynamicObject(19477, 0.000000, 0.000000, 0.000000, 0.000000, 0.000000, 0.000000);
    SetDynamicObjectMaterialText(tmpobjid, 0, "LSPD", 140, "Impact", 80, 0, -1, 0, 1);
    AttachDynamicObjectToVehicle(tmpobjid, boost_lspdcar, 1.042553, -1.651380, 0.209145, 0.000000, -13.300002, -361.600158);
    tmpobjid = CreateDynamicObject(19477, 0.000000, 0.000000, 0.000000, 0.000000, 0.000000, 0.000000);
    SetDynamicObjectMaterialText(tmpobjid, 0, "~", 100, "Webdings", 90, 0, -65536, 0, 1);
    AttachDynamicObjectToVehicle(tmpobjid, boost_lspdcar, -0.001300, -1.342470, 0.594511, 0.000000, -59.299953, -90.000000);
    boost_veh_lvl[boost_lspdcar] = 10;
    braking_veh_lvl[boost_lspdcar] = 8;
    sapdpatrul[1] = CreateVehicle(560,1536.01879883,-1676.16955566,13.20281219);
    markcarid[0][4]=sapdpatrul[1];
    tmpobjid = CreateDynamicObject(19797,0.0,0.0,-1000.0,0.0,0.0,0.0,0,0,-1,300.0,300.0);
    AttachDynamicObjectToVehicle(tmpobjid, sapdpatrul[1], -0.226, 2.628, -0.210, 0.000, 0.000, 179.900);
    tmpobjid = CreateDynamicObject(19797,0.0,0.0,-1000.0,0.0,0.0,0.0,0,0,-1,300.0,300.0);
    AttachDynamicObjectToVehicle(tmpobjid, sapdpatrul[1], 0.243, 2.619, -0.210, 0.000, 0.000, 179.199);
    tmpobjid = CreateDynamicObject(19797,0.0,0.0,-1000.0,0.0,0.0,0.0,0,0,-1,300.0,300.0);
    AttachDynamicObjectToVehicle(tmpobjid, sapdpatrul[1], 1.071, 1.159, 0.140, 0.000, 0.000, 88.799);
    tmpobjid = CreateDynamicObject(19797,0.0,0.0,-1000.0,0.0,0.0,0.0,0,0,-1,300.0,300.0);
    AttachDynamicObjectToVehicle(tmpobjid, sapdpatrul[1], -1.075, 1.113, 0.150, 0.000, 0.000, -85.500);
    tmpobjid = CreateDynamicObject(19620,0.0,0.0,-1000.0,0.0,0.0,0.0,0,0,-1,300.0,300.0);
    AttachDynamicObjectToVehicle(tmpobjid, sapdpatrul[1], 0.000, 0.000, 0.810, 0.000, 0.000, 0.000);
    tmpobjid = CreateDynamicObject(19893,0.0,0.0,-1000.0,0.0,0.0,0.0,0,0,-1,300.0,300.0);
    AttachDynamicObjectToVehicle(tmpobjid, sapdpatrul[1], 0.000, 0.400, -0.150, 0.000, 0.000, 0.000);
    tmpobjid = CreateDynamicObject(19477, 0.000000, 0.000000, 0.000000, 0.000000, 0.000000, 0.000000);
    SetDynamicObjectMaterialText(tmpobjid, 0, "LSPD", 140, "Impact", 80, 0, -1, 0, 1);
    AttachDynamicObjectToVehicle(tmpobjid, sapdpatrul[1], -0.007103, 1.924879, 0.310746, -0.699963, -77.499931, -269.801422);
    tmpobjid = CreateDynamicObject(19477, 0.000000, 0.000000, 0.000000, 0.000000, 0.000000, 0.000000);
    SetDynamicObjectMaterialText(tmpobjid, 0, "LSPD", 140, "Impact", 80, 0, -1, 0, 1);
    AttachDynamicObjectToVehicle(tmpobjid, sapdpatrul[1], -1.021961, -1.717599, 0.238558, 0.000000, -13.300002, -178.700073);
    tmpobjid = CreateDynamicObject(19477, 0.000000, 0.000000, 0.000000, 0.000000, 0.000000, 0.000000);
    SetDynamicObjectMaterialText(tmpobjid, 0, "LSPD", 140, "Impact", 80, 0, -1, 0, 1);
    AttachDynamicObjectToVehicle(tmpobjid, sapdpatrul[1], 1.042553, -1.651380, 0.209145, 0.000000, -13.300002, -361.600158);

	sapdcar[0] = CreateVehicle(601,1534.7269, -1644.3800, 4.8840); // lspd tank
    carvid = CreateVehicle(541,1602.53906, -1700.09998, 5.70376);
    tmpobjid = CreateDynamicObject(19797,0.0,0.0,-1000.0,0.0,0.0,0.0,0,0,-1,300.0,300.0);
    AttachDynamicObjectToVehicle(tmpobjid, carvid, 0.261, 2.427, -0.220, 0.000, 0.000, 176.500);
    tmpobjid = CreateDynamicObject(19797,0.0,0.0,-1000.0,0.0,0.0,0.0,0,0,-1,300.0,300.0);
    AttachDynamicObjectToVehicle(tmpobjid, carvid, -0.259, 2.438, -0.210, 0.000, 179.800, 179.900);
    tmpobjid = CreateDynamicObject(18646,0.0,0.0,-1000.0,0.0,0.0,0.0,0,0,-1,300.0,300.0);
    AttachDynamicObjectToVehicle(tmpobjid, carvid, -0.370, -0.210, 0.690, 0.000, 0.000, 0.000);
    tmpobjid = CreateDynamicObject(19477, 0.000000, 0.000000, 0.000000, 0.000000, 0.000000, 0.000000);
    SetDynamicObjectMaterialText(tmpobjid, 0, "LSPD", 140, "Impact", 90, 0, -1, 0, 1);
    AttachDynamicObjectToVehicle(tmpobjid, carvid, 0.023265, 1.374218, 0.298810, 0.000000, -88.199943, 89.299964);
    tmpobjid = CreateDynamicObject(19477, 0.000000, 0.000000, 0.000000, 0.000000, 0.000000, 0.000000);
    SetDynamicObjectMaterialText(tmpobjid, 0, "LSPD", 140, "Impact", 45, 0, -1, 0, 1);
    AttachDynamicObjectToVehicle(tmpobjid, carvid, 1.052642, -1.736079, 0.198805, 0.000000, 0.000000, 0.000000);
    tmpobjid = CreateDynamicObject(19477, 0.000000, 0.000000, 0.000000, 0.000000, 0.000000, 0.000000);
    SetDynamicObjectMaterialText(tmpobjid, 0, "LSPD", 140, "Impact", 45, 0, -1, 0, 1);
    AttachDynamicObjectToVehicle(tmpobjid, carvid, -1.049396, -1.664940, 0.208806, 0.000000, 0.000000, -178.199951);
    bulletboostls = CreateVehicle(541,1602.53906, -1696.08984, 5.70376);
    tmpobjid = CreateDynamicObject(19797,0.0,0.0,-1000.0,0.0,0.0,0.0,0,0,-1,300.0,300.0);
    AttachDynamicObjectToVehicle(tmpobjid, bulletboostls, 0.261, 2.427, -0.220, 0.000, 0.000, 176.500);
    tmpobjid = CreateDynamicObject(19797,0.0,0.0,-1000.0,0.0,0.0,0.0,0,0,-1,300.0,300.0);
    AttachDynamicObjectToVehicle(tmpobjid, bulletboostls, -0.259, 2.438, -0.210, 0.000, 179.800, 179.900);
    tmpobjid = CreateDynamicObject(18646,0.0,0.0,-1000.0,0.0,0.0,0.0,0,0,-1,300.0,300.0);
    AttachDynamicObjectToVehicle(tmpobjid, bulletboostls, -0.370, -0.210, 0.690, 0.000, 0.000, 0.000);
    tmpobjid = CreateDynamicObject(19477, 0.000000, 0.000000, 0.000000, 0.000000, 0.000000, 0.000000);
    SetDynamicObjectMaterialText(tmpobjid, 0, "LSPD", 140, "Impact", 90, 0, -1, 0, 1);
    AttachDynamicObjectToVehicle(tmpobjid, bulletboostls, 0.023265, 1.374218, 0.298810, 0.000000, -88.199943, 89.299964);
    tmpobjid = CreateDynamicObject(19477, 0.000000, 0.000000, 0.000000, 0.000000, 0.000000, 0.000000);
    SetDynamicObjectMaterialText(tmpobjid, 0, "LSPD", 140, "Impact", 45, 0, -1, 0, 1);
    AttachDynamicObjectToVehicle(tmpobjid, bulletboostls, 1.052642, -1.736079, 0.198805, 0.000000, 0.000000, 0.000000);
    tmpobjid = CreateDynamicObject(19477, 0.000000, 0.000000, 0.000000, 0.000000, 0.000000, 0.000000);
    SetDynamicObjectMaterialText(tmpobjid, 0, "LSPD", 140, "Impact", 45, 0, -1, 0, 1);
    AttachDynamicObjectToVehicle(tmpobjid, bulletboostls, -1.049396, -1.664940, 0.208806, 0.000000, 0.000000, -178.199951);
    tmpobjid = CreateDynamicObject(19477, 0.000000, 0.000000, 0.000000, 0.000000, 0.000000, 0.000000);
    SetDynamicObjectMaterialText(tmpobjid, 0, "~", 100, "Webdings", 90, 0, -65536, 0, 1);
    AttachDynamicObjectToVehicle(tmpobjid, bulletboostls, -0.009455, -1.101057, 0.501685, 0.000000, -80.199905, -90.001144);
    boost_veh_lvl[bulletboostls] = 10;
    braking_veh_lvl[bulletboostls] = 8;
	CreateVehicle(601,1526.4313, -1644.7328, 6.0031); // lspd 
	CreateVehicle(601,1530.5684, -1644.7328, 6.0031); // lspd 
	CreateVehicle(523,1547.27758789,-1674.60498047,5.45539999); //HPV1000
	CreateVehicle(523,1547.42431641,-1676.89062500,5.45539999); //HPV1000
	CreateVehicle(523,1547.27758789,-1672.40002441,5.45539999); //HPV1000
	CreateVehicle(523,1547.27758789,-1670.20007324,5.45539999); //HPV1000
	CreateVehicle(523,1547.27758789,-1668.00000000,5.45539999); //HPV1000
	CreateVehicle(523,1547.42431641,-1679.00000000,5.45539999); //HPV1000
	CreateVehicle(523,1547.42431641,-1681.09997559,5.45539999); //HPV1000
	CreateVehicle(497,1555.352,-1610.253,13.561); // lspd mav
	sapdcar[1] = CreateVehicle(427,1538.1487,-1644.9996,6.0336,180.0003,1,79, 600); // LSPD Enforcer 0
