appService.service('NamespaceTagService', ['$resource', '$q', 'AppUtil', function ($resource, $q, AppUtil) {
    var resource = $resource('', {}, {
        find_namespace_tags: {
            method: 'GET',
            isArray: false,
            url: AppUtil.prefixPath() + '/apps/:appId/envs/:env/clusters/:clusterName/namespaces/:namespaceName/tag/branchs'
        },
		find_namespace_tag: {
            method: 'GET',
            isArray: false,
            url: AppUtil.prefixPath() + '/apps/:appId/envs/:env/clusters/:clusterName/namespaces/:namespaceName/tag/branch/:tag'
        },
        create_tag: {
            method: 'POST',
            isArray: false,
            url: AppUtil.prefixPath() + '/apps/:appId/envs/:env/clusters/:clusterName/namespaces/:namespaceName/tag/branch/:tag'
        },
        delete_tag: {
            method: 'DELETE',
            isArray: false,
            url: AppUtil.prefixPath() + '/apps/:appId/envs/:env/clusters/:clusterName/namespaces/:namespaceName/tag/branchs/:branchName'
        }
    });

    function find_namespace_tags(appId, env, clusterName, namespaceName) {
        var d = $q.defer();
        resource.find_namespace_tags({
                                           appId: appId,
                                           env: env,
                                           clusterName: clusterName,
                                           namespaceName: namespaceName
                                       },
                                       function (result) {
                                           d.resolve(result);
                                       }, function (result) {
                d.reject(result);
            });
        return d.promise;
    }

	function find_namespace_tag(appId, env, clusterName, namespaceName, tag) {
        var d = $q.defer();
        resource.find_namespace_tag({
                                           appId: appId,
                                           env: env,
                                           clusterName: clusterName,
                                           namespaceName: namespaceName,
										   tag: tag
                                       },
                                       function (result) {
                                           d.resolve(result);
                                       }, function (result) {
                d.reject(result);
            });
        return d.promise;
    }

    function create_tag(appId, env, clusterName, namespaceName, tag) {
        var d = $q.defer();
        resource.create_tag({
                                   appId: appId,
                                   env: env,
                                   clusterName: clusterName,
                                   namespaceName: namespaceName,
								   tag: tag	
                               }, {},
                               function (result) {
                                   d.resolve(result);
                               }, function (result) {
                d.reject(result);
            });
        return d.promise;
    }

    function delete_tag(appId, env, clusterName, namespaceName, branchName, tag) {
        var d = $q.defer();
        resource.delete_tag({
                                   appId: appId,
                                   env: env,
                                   clusterName: clusterName,
                                   namespaceName: namespaceName,
                                   branchName: branchName,
								   tag: tag	
                               },
                               function (result) {
                                   d.resolve(result);
                               }, function (result) {
                d.reject(result);
            });
        return d.promise;
    }

    return {
        findNamespaceTags: find_namespace_tags,
		findNamespaceTag: find_namespace_tag,
        createTag: create_tag,
        deleteTag: delete_tag
    }
}]);
