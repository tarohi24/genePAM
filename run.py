import subprocess
import yaml

CONFIG_FILE = './config.yaml'


def run():
    with open(CONFIG_FILE) as f:
        conf = yaml.load(f)

    topics = [(int(i), int(j)) for i, j in conf['topics']]
    n_iter = conf['num_iterations']

    datadir = './data/' + conf['dataset']
    datafile = datadir + '/data.txt'
    genefile = datadir + '/genes.txt'

    outputfile_fmt = conf['output_dir'] +  '/{0}-{1}-{2}-{3}'
    
    jarfile = conf['jar']
    mem_limit = conf['mem_limit']

    procs = []
    for super_, sub in topics:
        cmd = ['java',
               '-Xmx{0:d}g'.format(mem_limit) if mem_limit != -1 else '',
               '-jar',
               jarfile,
               str(super_),
               str(sub),
               str(n_iter),
               datafile,
               genefile,
               outputfile_fmt.format(super_, sub, n_iter, 'super.txt'),
               outputfile_fmt.format(super_, sub, n_iter, 'sub.txt'),
               outputfile_fmt.format(super_, sub, n_iter, 'model.txt'),
               outputfile_fmt.format(super_, sub, n_iter, 'words.txt')]
        print(' '.join(cmd))
        procs.append(subprocess.Popen(' '.join(cmd), shell=True))

    for p in procs:
        p.wait()


if __name__ == '__main__':
    run()
