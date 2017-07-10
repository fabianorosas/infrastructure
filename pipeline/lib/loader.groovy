/*
  We are not using Jenkins global libraries due to the inability to
  load them from a directory other than the repository root.

  https://issues.jenkins-ci.org/browse/JENKINS-38609

  For now, this file will centralize 'load' logic to avoid loading a
  file multiple times and name clashes.
 */


//  The cache is a tree of FileLoader objects with loaded files as
//  leaves.
//
//  Loading the files:
//    'a/b/c/d.groovy'
//    'a/b/e.groovy'
//
//  Will result in:
//    loadedFilesCache = [a:
//                        [b:
//                         [c:
//                          [d: <loaded d.groovy>]
//                         ],
//                         [e: <loaded e.groovy>]
//                        ]
//                       ]
loadedFilesCache = new FileLoader()

ROOT_PATH = 'infrastructure/pipeline/'


class FileLoader implements Serializable {
  static loadFn

  // See propertyMissing in
  // http://groovy-lang.org/metaprogramming.html
  def propertyMissing(String name) {
    if (name == "groovy") {
      this."$name" = FileLoader.loadFn()
    } else {
      this."$name" = new FileLoader()
    }
    return this."$name"
  }
}


// The cachedLoad function works as if it did:
//
// loadedFilesCache.a.b.c.d.groovy = load 'a/b/c/d.groovy
// return loadedFilesCache.a.b.c.d.groovy
//
// Due to the FileLoader class implementation, non-existent nodes get
// created and the call to 'load' happens only once between cachedLoad
// calls.
//
// Intended usage is
// d = cachedLoad('a/b/c/d.groovy')

def cachedLoad(String filePath) {
  path = ROOT_PATH + filePath

  if (!path.endsWith('.groovy') && !path.endsWith('/groovy')) {
    path += '/groovy'
  } else {
    path = path.replace('.groovy', '/groovy')
  }

  FileLoader.loadFn = {String _path -> load(_path)}.curry(path)

  curr = pipeline
  for (part in filePath.split('/')) {
    // if curr."$part" doesn't exist, it will trigger the
    // propertyMissing method of FileLoader class
    curr = curr."$part"
  }

  return curr
}
