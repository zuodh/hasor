var copyDataMap = {
    "default"   : @@inner_dataway_sql(apiId,newScript)<%
        insert into interface_release (
            pub_api_id, pub_method, pub_path,   pub_status,
            pub_type,   pub_script, pub_schema, pub_sample,
            pub_script_ori,pub_release_time
        ) select
            api_id,     api_method,  api_path,   0,
            api_type,   #{newScript},api_schema, api_sample,
            api_script, now()
        from interface_info where api_id = #{apiId}
        limit 1;
    %>
};

var updatePublishMap = {
    "default"   : @@inner_dataway_sql(apiId)<% update interface_info set api_status = 1, api_gmt_time = now() where api_id = #{apiId} %>
};

var res = copyDataMap[dbMapping](${apiId},${newScript});
if (res == 0) {
    throw 500, "copy Data to release failed.";
}
var res = updatePublishMap[dbMapping](${apiId});
if (res == 0) {
    throw 500, "update publish failed.";
}
return true;