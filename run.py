import subprocess
import sys
import yaml

CONFIG_FILE = './config.yaml'


def run(is_pam=True):
    with open(CONFIG_FILE) as f:
        conf = yaml.load(f)

    if is_pam:
        topics = [(int(i), int(j)) for i, j in conf['topics']]
    else:
        topics = [int(i) for i in conf['topics']]
    n_iter = conf['num_iterations']

    datadir = './data/' + conf['dataset']
    datafile = datadir + '/data.txt'
    genefile = datadir + '/genes.txt'

    if is_pam:
        outputfile_fmt = conf['output_dir'] +  '/{0}-{1}-{2}-{3}'
    else:
        outputfile_fmt = conf['output_dir'] +  '/{0}-{1}'
    
    jarfile = conf['jar']
    mem_limit = conf['mem_limit']

    procs = []
    if is_pam:
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

    else:
        for t in topics:
            cmd = ['java',
                   '-Xmx{0:d}g'.format(mem_limit) if mem_limit != -1 else '',
                   '-jar',
                   jarfile,
                   str(t),
                   datafile,
                   genefile,
                   outputfile_fmt.format(t, 'theta.txt'),
                   outputfile_fmt.format(t, 'phi.txt')]
            print(' '.join(cmd))
            procs.append(subprocess.Popen(' '.join(cmd), shell=True))

    for p in procs:
        p.wait()


if __name__ == '__main__':
    is_pam = False if sys.argv[1] == 'LDA' else True
    run(is_pam)
